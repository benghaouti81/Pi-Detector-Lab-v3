/*
 * ============================================================
 * PI METAL DETECTOR - LEONARDO ETS FIRMWARE
 * Final corrected version
 *
 * MCU:
 *   ATmega32U4
 *   Arduino Leonardo
 *
 * F_CPU:
 *   16 MHz
 *
 * ETS:
 *   14 physical pulses
 *   5 ADC conversions / pulse
 *   70 reconstructed samples
 *
 * Reconstruction:
 *
 *   index = pulse + slot * 14
 *
 *   pulse = 0..13
 *   slot  = 0..4
 *
 * ============================================================
 */

#include <Arduino.h>
#include <avr/interrupt.h>
#include <stdint.h>
#include <string.h>


/* ============================================================
 * HARDWARE
 * ============================================================
 */

#define TX_PIN          9       // Leonardo D9 = PB5
#define RX_PIN          A0      // ADC7

#define TX_ACTIVE_HIGH  1


/* ============================================================
 * CLOCK
 * ============================================================
 */

#define CPU_HZ 16000000UL


/* ============================================================
 * ETS
 * ============================================================
 */

#define ETS_PULSES          14
#define ETS_SLOTS           5
#define ETS_SAMPLES         70


/*
 * One ETS phase:
 *
 * 1.6 us
 *
 * At 16 MHz:
 *
 * 1.6 us = 25.6 CPU cycles
 */
static inline uint16_t ets_ticks_to_cpu(uint16_t n)
{
    uint32_t x;

    x = ((uint32_t)n * 256UL + 5UL) / 10UL;

    if (x > 65535UL)
        x = 65535UL;

    return (uint16_t)x;
}


/*
 * Five samples belonging to one physical pulse are separated
 * by:
 *
 * 14 × 1.6 us = 22.4 us
 */
static inline uint16_t ets_slot_offset(uint8_t slot)
{
    return ets_ticks_to_cpu(
        (uint16_t)slot * ETS_PULSES
    );
}


/* ============================================================
 * DEFAULT DETECTOR SETTINGS
 * ============================================================
 */

#define DEFAULT_FREQUENCY_HZ   200
#define DEFAULT_PULSE_US       150

/*
 * 1 unit = 1.6 us
 */
#define DEFAULT_DELAY_TICKS     10

#define MIN_FREQUENCY_HZ        20
#define MAX_FREQUENCY_HZ        500

#define MIN_PULSE_US            10
#define MAX_PULSE_US            500

#define MIN_DELAY_TICKS         2
#define MAX_DELAY_TICKS         50


/* ============================================================
 * TRIPLE BUFFER
 * ============================================================
 */

#define BUFFER_COUNT 3

enum
{
    BUF_FREE = 0,
    BUF_FILLING,
    BUF_READY,
    BUF_SENDING
};

volatile uint16_t sampleBuffer
    [BUFFER_COUNT][ETS_SAMPLES];

volatile uint8_t bufferState
    [BUFFER_COUNT];

volatile uint32_t bufferSequence
    [BUFFER_COUNT];

volatile uint8_t captureBuffer = 0xFF;


/* ============================================================
 * FRAME / ACQUISITION STATE
 * ============================================================
 */

volatile uint8_t detectorRunning = 0;

volatile uint8_t acquisitionActive = 0;

volatile uint8_t etsFrameActive = 0;

volatile uint8_t etsPulse = 0;

volatile uint8_t triggerSlot = 0;

volatile uint8_t adcResultSlot = 0;


/* ============================================================
 * SETTINGS
 * ============================================================
 */

volatile uint16_t pendingFrequency =
    DEFAULT_FREQUENCY_HZ;

volatile uint16_t pendingPulseUs =
    DEFAULT_PULSE_US;

volatile uint16_t pendingDelayTicks =
    DEFAULT_DELAY_TICKS;

volatile uint16_t activeFrequency =
    DEFAULT_FREQUENCY_HZ;

volatile uint16_t activePulseUs =
    DEFAULT_PULSE_US;

volatile uint16_t activeDelayTicks =
    DEFAULT_DELAY_TICKS;


/* ============================================================
 * SEQUENCE / STATISTICS
 * ============================================================
 */

volatile uint32_t frameSequence = 0;

volatile uint32_t framesCompleted = 0;

volatile uint32_t framesDropped = 0;


/* ============================================================
 * COMMAND BUFFER
 * ============================================================
 */

#define COMMAND_SIZE 64

char commandBuffer[COMMAND_SIZE];

uint8_t commandLength = 0;


/* ============================================================
 * PACKET V1
 * ============================================================
 */

#define PACKET_SIZE      162
#define PAYLOAD_LENGTH   154

#define SYNC0             0xF5
#define SYNC1             0x5A
#define PROTOCOL_VERSION  0x01
#define RAW_BLOCK         0x01


/* ============================================================
 * USB PACKET TRANSMISSION STATE
 * ============================================================
 */

static uint8_t txPacket[PACKET_SIZE];

static uint16_t txPacketOffset = 0;

static uint8_t txBuffer = 0xFF;

static uint8_t txPacketActive = 0;


/* ============================================================
 * BUFFER FUNCTIONS
 * ============================================================
 */

static uint8_t findFreeBuffer()
{
    for (uint8_t i = 0; i < BUFFER_COUNT; i++)
    {
        if (bufferState[i] == BUF_FREE)
            return i;
    }

    return 0xFF;
}


static uint8_t findReadyBuffer()
{
    for (uint8_t i = 0; i < BUFFER_COUNT; i++)
    {
        if (bufferState[i] == BUF_READY)
            return i;
    }

    return 0xFF;
}


/*
 * Must be called with interrupts disabled or from ISR.
 */
static uint8_t reserveCaptureBuffer()
{
    uint8_t b;

    b = findFreeBuffer();

    if (b == 0xFF)
    {
        captureBuffer = 0xFF;
        return 0xFF;
    }

    bufferState[b] = BUF_FILLING;

    captureBuffer = b;

    return b;
}


/* ============================================================
 * CRC
 * ============================================================
 */

static uint16_t crc16_ccitt(
    const uint8_t *data,
    uint16_t length)
{
    uint16_t crc = 0xFFFF;

    for (uint16_t i = 0; i < length; i++)
    {
        crc ^= (uint16_t)data[i] << 8;

        for (uint8_t b = 0; b < 8; b++)
        {
            if (crc & 0x8000)
            {
                crc = (uint16_t)
                    ((crc << 1) ^ 0x1021);
            }
            else
            {
                crc <<= 1;
            }
        }
    }

    return crc;
}


/* ============================================================
 * LITTLE-ENDIAN HELPERS
 * ============================================================
 */

static inline void put16(
    uint8_t *p,
    uint16_t v)
{
    p[0] = (uint8_t)(v & 0xFF);
    p[1] = (uint8_t)(v >> 8);
}


static inline void put32(
    uint8_t *p,
    uint32_t v)
{
    p[0] = (uint8_t)(v & 0xFF);
    p[1] = (uint8_t)(v >> 8);
    p[2] = (uint8_t)(v >> 16);
    p[3] = (uint8_t)(v >> 24);
}


/* ============================================================
 * PACKET CREATION
 * ============================================================
 */

static void buildPacket(
    uint8_t buffer,
    uint8_t *packet)
{
    packet[0] = SYNC0;
    packet[1] = SYNC1;

    packet[2] = PROTOCOL_VERSION;
    packet[3] = RAW_BLOCK;

    put16(
        &packet[4],
        PAYLOAD_LENGTH
    );

    put32(
        &packet[6],
        bufferSequence[buffer]
    );

    /*
     * Timestamp not implemented.
     */
    put32(
        &packet[10],
        0
    );

    put16(
        &packet[14],
        activeDelayTicks
    );

    put16(
        &packet[16],
        ETS_SAMPLES
    );

    /*
     * 70 × uint16
     */
    for (uint8_t i = 0; i < ETS_SAMPLES; i++)
    {
        put16(
            &packet[18 + ((uint16_t)i * 2)],
            sampleBuffer[buffer][i]
        );
    }

    /*
     * Flags.
     */
    put16(
        &packet[158],
        0
    );

    /*
     * CRC over bytes 0..159.
     */
    uint16_t crc =
        crc16_ccitt(packet, 160);

    put16(
        &packet[160],
        crc
    );
}


/* ============================================================
 * ADC
 * ============================================================
 *
 * IMPORTANT:
 *
 * ADC AUTO TRIGGER IS NOT USED.
 *
 * Timer1 COMPB starts each conversion directly by setting ADSC.
 *
 * This is intentional for this version.
 *
 * ============================================================
 */

static void adcStop()
{
    /*
     * Stop ADC interrupt.
     *
     * No ADATE is used in this version.
     */
    ADCSRA &= (uint8_t)
        ~_BV(ADIE);

    acquisitionActive = 0;

    TIMSK1 &= (uint8_t)
        ~_BV(OCIE1B);
}


/*
 * Calculate Timer1 compare point.
 */
static uint16_t calculateSampleOffset(
    uint8_t slot)
{
    uint16_t delayCycles;
    uint16_t slotCycles;

    delayCycles =
        ets_ticks_to_cpu(activeDelayTicks);

    slotCycles =
        ets_slot_offset(slot);

    return delayCycles + slotCycles;
}


/*
 * Start ADC acquisition for the CURRENT physical pulse.
 *
 * This function does NOT start a new ETS frame.
 */
static void adcAcquisitionStart()
{
    uint16_t firstOffset;

    if (captureBuffer == 0xFF)
    {
        if (reserveCaptureBuffer() == 0xFF)
        {
            framesDropped++;
            return;
        }
    }

    if (bufferState[captureBuffer] != BUF_FILLING)
    {
        if (reserveCaptureBuffer() == 0xFF)
        {
            framesDropped++;
            return;
        }
    }

    acquisitionActive = 1;

    triggerSlot = 0;

    adcResultSlot = 0;

    /*
     * Local acquisition timebase.
     */
    TCNT1 = 0;

    firstOffset =
        calculateSampleOffset(0);

    /*
     * Minimum safety offset.
     */
    if (firstOffset < 8)
        firstOffset = 8;

    OCR1B = firstOffset;

    /*
     * Clear pending Timer1 COMPB flag.
     */
    TIFR1 = _BV(OCF1B);

    /*
     * Enable Timer1 COMPB interrupt.
     */
    TIMSK1 |= _BV(OCIE1B);

    /*
     * No ADC auto-trigger.
     *
     * ADC conversion will be started directly
     * from TIMER1_COMPB_vect by ADSC.
     */

    /*
     * Clear any old ADC completion flag.
     */
    ADCSRA |= _BV(ADIF);

    /*
     * Enable ADC interrupt.
     */
    ADCSRA |= _BV(ADIE);
}


/* ============================================================
 * TIMER1 COMPARE B
 * ============================================================
 */

ISR(TIMER1_COMPB_vect)
{
    if (!acquisitionActive)
    {
        TIMSK1 &= (uint8_t)
            ~_BV(OCIE1B);

        return;
    }

    /*
     * --------------------------------------------------------
     * Start the ADC conversion for the current slot.
     * --------------------------------------------------------
     *
     * IMPORTANT:
     *
     * We intentionally start ADC manually here.
     *
     * This replaces ADC Auto Trigger.
     */
    ADCSRA |= _BV(ADSC);

    /*
     * Slot 4 is the last slot.
     *
     * No more Timer1 compares are required.
     */
    if (triggerSlot >= ETS_SLOTS - 1)
    {
        TIMSK1 &= (uint8_t)
            ~_BV(OCIE1B);

        return;
    }

    /*
     * Schedule next ADC trigger.
     */
    triggerSlot++;

    OCR1B =
        calculateSampleOffset(triggerSlot);
}


/* ============================================================
 * ADC COMPLETE
 * ============================================================
 */

ISR(ADC_vect)
{
    uint16_t value;
    uint8_t buffer;
    uint8_t slot;
    uint8_t index;

    /*
     * Read ADC result.
     *
     * ADCL must be read before ADCH.
     *
     * Reading ADC as a 16-bit register performs this correctly
     * on AVR.
     */
    value = ADC;

    if (!acquisitionActive)
        return;

    if (!etsFrameActive)
        return;

    buffer = captureBuffer;

    if (buffer == 0xFF)
        return;

    slot = adcResultSlot;

    if (slot >= ETS_SLOTS)
        return;

    /*
     * ETS reconstruction:
     *
     * pulse 0:
     *   0 14 28 42 56
     *
     * pulse 1:
     *   1 15 29 43 57
     *
     * ...
     *
     * pulse 13:
     *   13 27 41 55 69
     */
    index =
        (uint8_t)
        (etsPulse +
         ((uint8_t)slot * ETS_PULSES));

    if (index < ETS_SAMPLES)
    {
        sampleBuffer[buffer][index] =
            value;
    }

    adcResultSlot++;

    /*
     * Five ADC results received.
     */
    if (adcResultSlot >= ETS_SLOTS)
    {
        /*
         * Stop ADC interrupt for this physical pulse.
         */
        ADCSRA &= (uint8_t)
            ~_BV(ADIE);

        TIMSK1 &= (uint8_t)
            ~_BV(OCIE1B);

        acquisitionActive = 0;

        adcResultSlot = 0;

        /*
         * Current physical pulse complete.
         */
        etsPulse++;

        /*
         * Do NOT reset etsPulse here.
         *
         * The next Timer3 pulse belongs to the same
         * ETS frame.
         */
    }
}


/* ============================================================
 * TIMER3
 * ============================================================
 *
 * F_CPU / 8
 *
 * 16 MHz / 8 = 2 MHz
 *
 * 1 tick = 0.5 us
 */


/* ============================================================
 * FREQUENCY
 * ============================================================
 */

static uint16_t frequencyToTicks(
    uint16_t frequency)
{
    uint32_t ticks;

    if (frequency < MIN_FREQUENCY_HZ)
        frequency = MIN_FREQUENCY_HZ;

    if (frequency > MAX_FREQUENCY_HZ)
        frequency = MAX_FREQUENCY_HZ;

    ticks =
        2000000UL /
        frequency;

    if (ticks < 2)
        ticks = 2;

    if (ticks > 65535UL)
        ticks = 65535UL;

    return (uint16_t)ticks;
}


/* ============================================================
 * PULSE WIDTH
 * ============================================================
 */

static uint16_t pulseToTicks(
    uint16_t pulseUs)
{
    uint32_t ticks;

    if (pulseUs < MIN_PULSE_US)
        pulseUs = MIN_PULSE_US;

    if (pulseUs > MAX_PULSE_US)
        pulseUs = MAX_PULSE_US;

    /*
     * 0.5 us/tick.
     */
    ticks =
        (uint32_t)pulseUs * 2UL;

    if (ticks < 1)
        ticks = 1;

    if (ticks > 65534UL)
        ticks = 65534UL;

    return (uint16_t)ticks;
}


/* ============================================================
 * APPLY SETTINGS
 * ============================================================
 *
 * Called at ETS frame boundaries.
 */
static void applySettings()
{
    uint16_t frequency;
    uint16_t pulse;
    uint16_t delay;

    uint16_t periodTicks;
    uint16_t pulseTicks;

    frequency = pendingFrequency;
    pulse = pendingPulseUs;
    delay = pendingDelayTicks;

    if (frequency < MIN_FREQUENCY_HZ)
        frequency = MIN_FREQUENCY_HZ;

    if (frequency > MAX_FREQUENCY_HZ)
        frequency = MAX_FREQUENCY_HZ;

    if (pulse < MIN_PULSE_US)
        pulse = MIN_PULSE_US;

    if (pulse > MAX_PULSE_US)
        pulse = MAX_PULSE_US;

    if (delay < MIN_DELAY_TICKS)
        delay = MIN_DELAY_TICKS;

    if (delay > MAX_DELAY_TICKS)
        delay = MAX_DELAY_TICKS;

    periodTicks =
        frequencyToTicks(frequency);

    pulseTicks =
        pulseToTicks(pulse);

    if (pulseTicks >= periodTicks)
        pulseTicks = periodTicks - 1;

    activeFrequency = frequency;
    activePulseUs = pulse;
    activeDelayTicks = delay;

    OCR3A =
        periodTicks - 1;

    OCR3B =
        pulseTicks - 1;
}


/* ============================================================
 * TIMER3 COMPA
 *
 * Start TX pulse.
 * ============================================================
 */

ISR(TIMER3_COMPA_vect)
{
    /*
     * Apply new settings ONLY at an ETS frame boundary.
     */
    if (!etsFrameActive &&
        !acquisitionActive)
    {
        applySettings();
    }

#if TX_ACTIVE_HIGH
    PORTB |= _BV(PB5);
#else
    PORTB &= (uint8_t)~_BV(PB5);
#endif
}


/* ============================================================
 * TIMER3 COMPB
 *
 * End TX pulse and begin acquisition.
 * ============================================================
 */

ISR(TIMER3_COMPB_vect)
{
#if TX_ACTIVE_HIGH
    PORTB &= (uint8_t)~_BV(PB5);
#else
    PORTB |= _BV(PB5);
#endif

    if (!detectorRunning)
        return;

    /*
     * A physical pulse cannot overlap its own acquisition.
     */
    if (acquisitionActive)
    {
        framesDropped++;
        return;
    }

    /*
     * Start a new ETS frame only if the previous
     * 14-pulse frame has completed.
     */
    if (!etsFrameActive)
    {
        /*
         * Need a capture buffer.
         */
        if (captureBuffer == 0xFF)
        {
            if (reserveCaptureBuffer() == 0xFF)
            {
                framesDropped++;
                return;
            }
        }

        /*
         * Make sure buffer is in FILLING state.
         */
        if (bufferState[captureBuffer] != BUF_FILLING)
        {
            if (reserveCaptureBuffer() == 0xFF)
            {
                framesDropped++;
                return;
            }
        }

        /*
         * Start a NEW 14-pulse ETS frame.
         */
        etsFrameActive = 1;

        etsPulse = 0;

        triggerSlot = 0;

        adcResultSlot = 0;
    }

    /*
     * Start ADC acquisition for the CURRENT
     * physical pulse.
     */
    adcAcquisitionStart();
}


/* ============================================================
 * COMPLETE ETS FRAME
 * ============================================================
 */

static void serviceFrameCompletion()
{
    uint8_t finished;
    uint8_t next;

    if (!etsFrameActive)
        return;

    /*
     * A frame is complete only after:
     *
     * pulse 0..13 have each received 5 samples.
     */
    if (etsPulse < ETS_PULSES)
        return;

    /*
     * ADC must not still be active.
     */
    if (acquisitionActive)
        return;

    noInterrupts();

    /*
     * Re-check while interrupts are disabled.
     */
    if (!etsFrameActive ||
        etsPulse < ETS_PULSES ||
        acquisitionActive)
    {
        interrupts();
        return;
    }

    finished = captureBuffer;

    if (finished == 0xFF)
    {
        etsFrameActive = 0;
        etsPulse = 0;

        interrupts();

        framesDropped++;

        return;
    }

    /*
     * Completed buffer becomes READY.
     */
    bufferSequence[finished] =
        frameSequence++;

    bufferState[finished] =
        BUF_READY;

    framesCompleted++;

    /*
     * Current frame finished.
     */
    etsFrameActive = 0;

    etsPulse = 0;

    /*
     * Immediately reserve another FREE buffer
     * for the next ETS frame if one exists.
     */
    next = findFreeBuffer();

    if (next != 0xFF)
    {
        bufferState[next] =
            BUF_FILLING;

        captureBuffer = next;
    }
    else
    {
        captureBuffer = 0xFF;
    }

    interrupts();
}


/* ============================================================
 * TIMER INITIALIZATION
 * ============================================================
 */

static void timer1Init()
{
    /*
     * Normal mode.
     *
     * Timer1 runs continuously at F_CPU.
     */
    TCCR1A = 0;

    TCCR1B = 0;

    TCNT1 = 0;

    OCR1B = 100;

    /*
     * No prescaler.
     */
    TCCR1B |= _BV(CS10);

    /*
     * No interrupt until acquisition starts.
     */
    TIMSK1 = 0;

    TIFR1 = _BV(OCF1B);
}


static void timer3Init()
{
    uint16_t periodTicks;
    uint16_t pulseTicks;

    TCCR3A = 0;

    TCCR3B = 0;

    /*
     * CTC mode.
     */
    TCCR3B |= _BV(WGM32);

    /*
     * F_CPU / 8.
     */
    TCCR3B |= _BV(CS31);

    TCNT3 = 0;

    periodTicks =
        frequencyToTicks(
            DEFAULT_FREQUENCY_HZ
        );

    pulseTicks =
        pulseToTicks(
            DEFAULT_PULSE_US
        );

    if (pulseTicks >= periodTicks)
        pulseTicks =
            periodTicks - 1;

    OCR3A =
        periodTicks - 1;

    OCR3B =
        pulseTicks - 1;

    TIMSK3 = 0;

    TIFR3 =
        _BV(OCF3A) |
        _BV(OCF3B);
}


/* ============================================================
 * ADC INITIALIZATION
 * ============================================================
 */

static void adcInit()
{
    /*
     * AVcc reference.
     *
     * ADC7 = A0.
     */
    ADMUX = 0;

    ADMUX |=
        _BV(MUX2) |
        _BV(MUX1) |
        _BV(MUX0);

    /*
     * Right adjusted.
     */
    ADMUX &= (uint8_t)
        ~_BV(ADLAR);

    /*
     * Disable digital input on ADC7.
     */
    DIDR0 |= _BV(ADC7D);

    /*
     * ADC clock:
     *
     * 16 MHz / 16 = 1 MHz.
     */
    ADCSRA = 0;

    ADCSRA |= _BV(ADPS2);

    ADCSRA |= _BV(ADEN);

    /*
     * IMPORTANT:
     *
     * Auto trigger disabled.
     */
    ADCSRA &= (uint8_t)
        ~_BV(ADATE);

    /*
     * ADC interrupt disabled until acquisition starts.
     */
    ADCSRA &= (uint8_t)
        ~_BV(ADIE);

    /*
     * Dummy conversion.
     */
    ADCSRA |= _BV(ADSC);

    while (ADCSRA & _BV(ADSC))
    {
        ;
    }

    /*
     * Clear ADC completion flag.
     */
    ADCSRA |= _BV(ADIF);
}


/* ============================================================
 * TX INITIALIZATION
 * ============================================================
 */

static void txInit()
{
    /*
     * Leonardo D9 = PB5.
     */
    DDRB |= _BV(PB5);

#if TX_ACTIVE_HIGH
    PORTB &= (uint8_t)~_BV(PB5);
#else
    PORTB |= _BV(PB5);
#endif
}


/* ============================================================
 * START
 * ============================================================
 */

static void detectorStart()
{
    noInterrupts();

    /*
     * Stop any unfinished acquisition.
     */
    ADCSRA &= (uint8_t)
        ~_BV(ADIE);

    TIMSK1 &= (uint8_t)
        ~_BV(OCIE1B);

    acquisitionActive = 0;

    etsFrameActive = 0;

    /*
     * Reset ETS state.
     */
    etsPulse = 0;

    triggerSlot = 0;

    adcResultSlot = 0;

    /*
     * If there is no valid capture buffer,
     * reserve one.
     */
    if (captureBuffer == 0xFF ||
        bufferState[captureBuffer] != BUF_FILLING)
    {
        reserveCaptureBuffer();
    }

    /*
     * Start detector.
     */
    detectorRunning = 1;

    /*
     * Reset Timer3.
     */
    TCNT3 = 0;

    /*
     * Apply current settings before starting.
     */
    applySettings();

    /*
     * Clear Timer3 flags.
     */
    TIFR3 =
        _BV(OCF3A) |
        _BV(OCF3B);

    /*
     * Enable TX pulse generation.
     */
    TIMSK3 =
        _BV(OCIE3A) |
        _BV(OCIE3B);

    interrupts();

    Serial.println(F("OK"));
}


/* ============================================================
 * STOP
 * ============================================================
 */

static void detectorStop()
{
    noInterrupts();

    detectorRunning = 0;

    /*
     * Stop Timer3 interrupts.
     */
    TIMSK3 = 0;

    /*
     * Stop Timer1.
     */
    TIMSK1 &= (uint8_t)
        ~_BV(OCIE1B);

    /*
     * Stop ADC interrupt.
     */
    ADCSRA &= (uint8_t)
        ~_BV(ADIE);

    acquisitionActive = 0;

    etsFrameActive = 0;

    etsPulse = 0;

    adcResultSlot = 0;

    /*
     * Cancel packet transmission state.
     *
     * Do NOT alter buffer states here.
     */
    txPacketActive = 0;

    txPacketOffset = 0;

    txBuffer = 0xFF;

#if TX_ACTIVE_HIGH
    PORTB &= (uint8_t)~_BV(PB5);
#else
    PORTB |= _BV(PB5);
#endif

    interrupts();

    Serial.println(F("OK"));
}


/* ============================================================
 * CONFIG
 * ============================================================
 */

static void sendConfig()
{
    Serial.print(F("#CONFIG:"));

    Serial.print(ETS_SAMPLES);

    /*
     * Sample spacing = 1600 ns.
     */
    Serial.print(F(",1600"));

    /*
     * ADC resolution = 10 bit.
     */
    Serial.print(F(",10"));

    Serial.print(F(",ETS"));

    Serial.print(F(","));
    Serial.print(ETS_PULSES);

    Serial.print(F(","));
    Serial.println(ETS_SLOTS);
}


/* ============================================================
 * COMMAND VALUES
 * ============================================================
 */

static long readNumber(
    const char *p)
{
    return atol(p);
}


static void setFrequency(
    long value)
{
    if (value < MIN_FREQUENCY_HZ)
        value = MIN_FREQUENCY_HZ;

    if (value > MAX_FREQUENCY_HZ)
        value = MAX_FREQUENCY_HZ;

    noInterrupts();

    pendingFrequency =
        (uint16_t)value;

    interrupts();

    Serial.println(F("OK"));
}


static void setPulse(
    long value)
{
    if (value < MIN_PULSE_US)
        value = MIN_PULSE_US;

    if (value > MAX_PULSE_US)
        value = MAX_PULSE_US;

    noInterrupts();

    pendingPulseUs =
        (uint16_t)value;

    interrupts();

    Serial.println(F("OK"));
}


static void setDelay(
    long value)
{
    if (value < MIN_DELAY_TICKS)
        value = MIN_DELAY_TICKS;

    if (value > MAX_DELAY_TICKS)
        value = MAX_DELAY_TICKS;

    noInterrupts();

    pendingDelayTicks =
        (uint16_t)value;

    interrupts();

    Serial.println(F("OK"));
}


/* ============================================================
 * COMMAND PARSER
 * ============================================================
 */

static void processCommand()
{
    char *cmd =
        commandBuffer;

    /*
     * START
     */
    if (strcmp(cmd, "START") == 0)
    {
        detectorStart();
        return;
    }

    /*
     * STOP
     */
    if (strcmp(cmd, "STOP") == 0)
    {
        detectorStop();
        return;
    }

    /*
     * PING
     */
    if (strcmp(cmd, "PING") == 0)
    {
        Serial.println(F("PONG"));
        return;
    }

    /*
     * CONFIG
     */
    if (strcmp(cmd, "CONFIG?") == 0 ||
        strcmp(cmd, "CONFIG") == 0 ||
        strcmp(cmd, "Q") == 0)
    {
        sendConfig();
        return;
    }

    /*
     * STATUS
     */
    if (strcmp(cmd, "STATUS") == 0)
    {
        Serial.print(F("#STATUS:RUN="));
        Serial.print(detectorRunning);

        Serial.print(F(",ACQ="));
        Serial.print(acquisitionActive);

        Serial.print(F(",SEQ="));
        Serial.print(frameSequence);

        Serial.print(F(",DONE="));
        Serial.print(framesCompleted);

        Serial.print(F(",DROP="));
        Serial.println(framesDropped);

        return;
    }

    /*
     * FREQ <Hz>
     */
    if (strncmp(cmd, "FREQ ", 5) == 0)
    {
        setFrequency(
            readNumber(cmd + 5)
        );

        return;
    }

    /*
     * SET:FREQ=<Hz>
     */
    if (strncmp(cmd, "SET:FREQ=", 9) == 0)
    {
        setFrequency(
            readNumber(cmd + 9)
        );

        return;
    }

    /*
     * PULSE <us>
     */
    if (strncmp(cmd, "PULSE ", 6) == 0)
    {
        setPulse(
            readNumber(cmd + 6)
        );

        return;
    }

    /*
     * SET:PULSE=<us>
     */
    if (strncmp(cmd, "SET:PULSE=", 10) == 0)
    {
        setPulse(
            readNumber(cmd + 10)
        );

        return;
    }

    /*
     * DELAY <ticks>
     */
    if (strncmp(cmd, "DELAY ", 6) == 0)
    {
        setDelay(
            readNumber(cmd + 6)
        );

        return;
    }

    /*
     * SET:DELAY=<ticks>
     */
    if (strncmp(cmd, "SET:DELAY=", 10) == 0)
    {
        setDelay(
            readNumber(cmd + 10)
        );

        return;
    }

    Serial.println(
        F("ERROR UNKNOWN_COMMAND")
    );
}


/* ============================================================
 * SERIAL SERVICE
 * ============================================================
 */

static void serialService()
{
    while (Serial.available() > 0)
    {
        char c =
            (char)Serial.read();

        /*
         * Command termination.
         */
        if (c == '\r' || c == '\n')
        {
            if (commandLength > 0)
            {
                commandBuffer[
                    commandLength] = '\0';

                processCommand();

                commandLength = 0;

                commandBuffer[0] = '\0';
            }

            continue;
        }

        /*
         * Prevent overflow.
         */
        if (commandLength <
            COMMAND_SIZE - 1)
        {
            commandBuffer[
                commandLength++] = c;

            commandBuffer[
                commandLength] = '\0';
        }
        else
        {
            commandLength = 0;

            commandBuffer[0] = '\0';

            Serial.println(
                F("ERROR COMMAND_TOO_LONG")
            );
        }
    }
}


/* ============================================================
 * SEND READY FRAME
 * ============================================================
 */

static void sendReadyFrame()
{
    uint8_t buffer;

    /*
     * USB not connected.
     */
    if (!Serial)
        return;

    /*
     * STEP 1
     *
     * If no packet is currently being transmitted,
     * search for a READY buffer.
     */
    if (!txPacketActive)
    {
        noInterrupts();

        buffer =
            findReadyBuffer();

        if (buffer == 0xFF)
        {
            interrupts();
            return;
        }

        /*
         * Protect this buffer from acquisition.
         */
        bufferState[buffer] =
            BUF_SENDING;

        txBuffer = buffer;

        /*
         * Build packet while buffer is protected.
         */
        buildPacket(
            buffer,
            txPacket
        );

        txPacketOffset = 0;

        txPacketActive = 1;

        interrupts();
    }

    /*
     * STEP 2
     *
     * Send as many bytes as USB can accept now.
     */
    int available =
        Serial.availableForWrite();

    if (available <= 0)
        return;

    uint16_t remaining =
        PACKET_SIZE -
        txPacketOffset;

    uint16_t chunk =
        (uint16_t)available;

    if (chunk > remaining)
        chunk = remaining;

    if (chunk == 0)
        return;

    size_t written =
        Serial.write(
            txPacket + txPacketOffset,
            chunk
        );

    txPacketOffset +=
        (uint16_t)written;

    /*
     * STEP 3
     *
     * Entire packet accepted by USB.
     */
    if (txPacketOffset >= PACKET_SIZE)
    {
        noInterrupts();

        if (txBuffer != 0xFF)
        {
            bufferState[txBuffer] =
                BUF_FREE;
        }

        txBuffer = 0xFF;

        txPacketOffset = 0;

        txPacketActive = 0;

        interrupts();
    }
}


/* ============================================================
 * SETUP
 * ============================================================
 */

void setup()
{
    Serial.begin(115200);

    txInit();

    timer1Init();

    timer3Init();

    adcInit();

    /*
     * Initialize all buffers.
     */
    for (uint8_t i = 0;
         i < BUFFER_COUNT;
         i++)
    {
        bufferState[i] =
            BUF_FREE;

        bufferSequence[i] =
            0;
    }

    /*
     * Reserve first capture buffer.
     */
    noInterrupts();

    captureBuffer =
        reserveCaptureBuffer();

    interrupts();

    /*
     * Initial states.
     */
    detectorRunning = 0;

    acquisitionActive = 0;

    etsFrameActive = 0;

    etsPulse = 0;

    triggerSlot = 0;

    adcResultSlot = 0;

    frameSequence = 0;

    framesCompleted = 0;

    framesDropped = 0;

    txPacketActive = 0;

    txPacketOffset = 0;

    txBuffer = 0xFF;

    /*
     * Allow USB enumeration.
     */
    unsigned long t =
        millis();

    while (!Serial &&
           millis() - t < 1500)
    {
        ;
    }

    Serial.println(F("#READY"));

    sendConfig();
}


/* ============================================================
 * LOOP
 * ============================================================
 */

void loop()
{
    /*
     * USB commands.
     */
    serialService();

    /*
     * Finish completed 14-pulse ETS frames.
     */
    serviceFrameCompletion();

    /*
     * Send completed frames.
     *
     * Transmission is non-blocking and progressive.
     */
    if (detectorRunning)
    {
        sendReadyFrame();
    }
}