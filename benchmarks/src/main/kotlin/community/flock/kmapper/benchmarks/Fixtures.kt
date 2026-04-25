package community.flock.kmapper.benchmarks

object Fixtures {
    val simpleUser = SimpleUser(firstName = "John", lastName = "Doe", age = 42)

    val complexOrder: Order = Order(
        id = "ord-0001",
        customerId = CustomerId("cust-77"),
        firstName = "John",
        lastName = "Doe",
        age = 42,
        billingAddress = Address(
            streetCity = StreetCity("Main Street 1", "Hamburg"),
            zipCode = "22049",
            country = "DE",
        ),
        shippingAddress = Address(
            streetCity = StreetCity("Side Street 99", "Berlin"),
            zipCode = "10115",
            country = "DE",
        ),
        lines = List(10) { i ->
            OrderLine(
                sku = "SKU-$i",
                quantity = i + 1,
                unitPrice = Money(cents = 100L * (i + 1)),
            )
        },
        status = OrderStatus.SHIPPED,
        total = Money(cents = 5_500L),
    )
}
