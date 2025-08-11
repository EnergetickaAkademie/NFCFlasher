package eu.swpelc.nfcflasher

enum class BuildingType(val byteValue: Byte) {
    CITY_CENTER(0),
    CITY_CENTER_A(1),
    CITY_CENTER_B(2),
    CITY_CENTER_C(3),
    CITY_CENTER_D(4),
    CITY_CENTER_E(5),
    CITY_CENTER_F(6),
    FACTORY(7),
    STADIUM(8),
    HOSPITAL(9),
    UNIVERSITY(10),
    AIRPORT(11),
    SHOPPING_MALL(12),
    TECHNOLOGY_CENTER(13),
    FARM(14),
    LIVING_QUARTER_SMALL(15),
    LIVING_QUARTER_LARGE(16),
    SCHOOL(17);

    companion object {
        fun fromByte(byteValue: Byte): BuildingType? {
            return entries.find { it.byteValue == byteValue }
        }
    }
}
