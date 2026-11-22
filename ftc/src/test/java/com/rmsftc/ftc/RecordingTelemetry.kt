package com.rmsftc.ftc

import org.firstinspires.ftc.robotcore.external.Func
import org.firstinspires.ftc.robotcore.external.Telemetry

/**
 * A fake [Telemetry] for unit tests: records how often data was added and
 * [update] was called, and does nothing else. (The FTC SDK has no such
 * class; tests that need a Telemetry use this.)
 */
class RecordingTelemetry : Telemetry {
    var addDataCalls = 0
    var updateCalls = 0

    override fun addData(caption: String, format: String, vararg args: Any?): Telemetry.Item {
        addDataCalls++
        return DummyItem
    }

    override fun addData(caption: String, value: Any?): Telemetry.Item {
        addDataCalls++
        return DummyItem
    }

    override fun <T> addData(caption: String, valueProducer: Func<T>): Telemetry.Item {
        addDataCalls++
        return DummyItem
    }

    override fun <T> addData(caption: String, format: String, valueProducer: Func<T>): Telemetry.Item {
        addDataCalls++
        return DummyItem
    }

    override fun removeItem(item: Telemetry.Item): Boolean = false

    override fun clear() {}

    override fun clearAll() {}

    override fun addAction(action: Runnable): Any = action

    override fun removeAction(token: Any): Boolean = false

    override fun speak(text: String) {}

    override fun speak(text: String, languageCode: String, countryCode: String) {}

    override fun update(): Boolean {
        updateCalls++
        return true
    }

    override fun addLine(): Telemetry.Line = DummyLine

    override fun addLine(lineCaption: String): Telemetry.Line = DummyLine

    override fun removeLine(line: Telemetry.Line): Boolean = false

    override fun isAutoClear(): Boolean = true

    override fun setAutoClear(autoClear: Boolean) {}

    override fun getMsTransmissionInterval(): Int = 250

    override fun setMsTransmissionInterval(msTransmissionInterval: Int) {}

    override fun getItemSeparator(): String = "\n"

    override fun setItemSeparator(itemSeparator: String) {}

    override fun getCaptionValueSeparator(): String = ": "

    override fun setCaptionValueSeparator(captionValueSeparator: String) {}

    override fun setDisplayFormat(displayFormat: Telemetry.DisplayFormat) {}

    override fun log(): Telemetry.Log = DummyLog

    private object DummyItem : Telemetry.Item {
        override fun getCaption(): String = ""
        override fun setCaption(caption: String): Telemetry.Item = this
        override fun setValue(format: String, vararg args: Any?): Telemetry.Item = this
        override fun setValue(value: Any?): Telemetry.Item = this
        override fun <T> setValue(valueProducer: Func<T>): Telemetry.Item = this
        override fun <T> setValue(format: String, valueProducer: Func<T>): Telemetry.Item = this
        override fun setRetained(retained: Boolean?): Telemetry.Item = this
        override fun isRetained(): Boolean = false
        override fun addData(caption: String, format: String, vararg args: Any?): Telemetry.Item = this
        override fun addData(caption: String, value: Any?): Telemetry.Item = this
        override fun <T> addData(caption: String, valueProducer: Func<T>): Telemetry.Item = this
        override fun <T> addData(caption: String, format: String, valueProducer: Func<T>): Telemetry.Item = this
    }

    private object DummyLine : Telemetry.Line {
        override fun addData(caption: String, format: String, vararg args: Any?): Telemetry.Item = DummyItem
        override fun addData(caption: String, value: Any?): Telemetry.Item = DummyItem
        override fun <T> addData(caption: String, valueProducer: Func<T>): Telemetry.Item = DummyItem
        override fun <T> addData(caption: String, format: String, valueProducer: Func<T>): Telemetry.Item = DummyItem
    }

    private object DummyLog : Telemetry.Log {
        override fun getCapacity(): Int = 0
        override fun setCapacity(capacity: Int) {}
        override fun getDisplayOrder(): Telemetry.Log.DisplayOrder = Telemetry.Log.DisplayOrder.OLDEST_FIRST
        override fun setDisplayOrder(displayOrder: Telemetry.Log.DisplayOrder) {}
        override fun add(entry: String) {}
        override fun add(format: String, vararg args: Any?) {}
        override fun clear() {}
    }
}
