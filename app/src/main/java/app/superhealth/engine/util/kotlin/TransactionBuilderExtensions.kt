package app.superhealth.engine.util.kotlin

import app.superhealth.engine.service.btle.AbstractBTLESingleDeviceSupport

inline fun AbstractBTLESingleDeviceSupport.withTransaction(
    name: String,
    block: (app.superhealth.engine.service.btle.TransactionBuilder) -> Unit
) {
    val builder = createTransactionBuilder(name)
    block(builder)
    builder.queue()
}
