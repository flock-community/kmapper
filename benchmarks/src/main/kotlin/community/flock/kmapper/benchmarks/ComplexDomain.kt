package community.flock.kmapper.benchmarks

import community.flock.kmapper.mapper

/**
 * The complex workload: nested data classes, value classes that need
 * unwrapping, a list of nested objects, an enum, a derived field, and
 * a numeric widening conversion. Designed to exercise as many kmapper
 * codegen paths as possible in a single mapping.
 */
@JvmInline
value class CustomerId(val value: String)

@JvmInline
value class Money(val cents: Long)

enum class OrderStatus { PENDING, SHIPPED, DELIVERED, CANCELLED }
enum class OrderStatusDto { PENDING, SHIPPED, DELIVERED, CANCELLED }

data class StreetCity(val street: String, val city: String)
data class StreetCityDto(val street: String, val city: String)

data class Address(
    val streetCity: StreetCity,
    val zipCode: String,
    val country: String,
)

data class AddressDto(
    val streetCity: StreetCityDto,
    val zipCode: String,
    val country: String,
)

data class OrderLine(
    val sku: String,
    val quantity: Int,
    val unitPrice: Money,
)

data class OrderLineDto(
    val sku: String,
    val quantity: Long,
    val unitPrice: Long,
)

data class Order(
    val id: String,
    val customerId: CustomerId,
    val firstName: String,
    val lastName: String,
    val age: Int,
    val billingAddress: Address,
    val shippingAddress: Address,
    val lines: List<OrderLine>,
    val status: OrderStatus,
    val total: Money,
)

data class OrderDto(
    val id: String,
    val customerId: String,
    val customerName: String,
    val age: Long,
    val billingAddress: AddressDto,
    val shippingAddress: AddressDto,
    val lines: List<OrderLineDto>,
    val status: OrderStatusDto,
    val totalCents: Long,
)

fun OrderLine.toDtoKMapper(): OrderLineDto = mapper {
    quantity = it.quantity.toLong()
    unitPrice = it.unitPrice.cents
}

fun OrderLine.toDtoManual(): OrderLineDto = OrderLineDto(
    sku = sku,
    quantity = quantity.toLong(),
    unitPrice = unitPrice.cents,
)

fun Order.toDtoKMapper(): OrderDto = mapper {
    customerName = "${it.firstName} ${it.lastName}"
    age = it.age.toLong()
    lines = it.lines.map { line -> line.toDtoKMapper() }
    status = OrderStatusDto.valueOf(it.status.name)
    totalCents = it.total.cents
}

fun Order.toDtoManual(): OrderDto = OrderDto(
    id = id,
    customerId = customerId.value,
    customerName = "$firstName $lastName",
    age = age.toLong(),
    billingAddress = AddressDto(
        streetCity = StreetCityDto(billingAddress.streetCity.street, billingAddress.streetCity.city),
        zipCode = billingAddress.zipCode,
        country = billingAddress.country,
    ),
    shippingAddress = AddressDto(
        streetCity = StreetCityDto(shippingAddress.streetCity.street, shippingAddress.streetCity.city),
        zipCode = shippingAddress.zipCode,
        country = shippingAddress.country,
    ),
    lines = lines.map { it.toDtoManual() },
    status = OrderStatusDto.valueOf(status.name),
    totalCents = total.cents,
)
