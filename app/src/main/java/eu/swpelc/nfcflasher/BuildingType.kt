package eu.swpelc.nfcflasher

import androidx.annotation.DrawableRes

enum class BuildingType(val byteValue: Byte, @DrawableRes val imageResId: Int) {
    CITY_CENTER(0, R.drawable.namesti),
    CITY_CENTER_A(1, R.drawable.namesti),
    CITY_CENTER_B(2, R.drawable.namesti),
    CITY_CENTER_C(3, R.drawable.namesti),
    CITY_CENTER_D(4, R.drawable.namesti),
    CITY_CENTER_E(5, R.drawable.namesti),
    CITY_CENTER_F(6, R.drawable.namesti),
    FACTORY(7, R.drawable.tovarna),
    STADIUM(8, R.drawable.stadion),
    HOSPITAL(9, R.drawable.nemocnice),
    UNIVERSITY(10, R.drawable.universita),
    AIRPORT(11, R.drawable.letiste),
    SHOPPING_MALL(12, R.drawable.obchodak),
    TECHNOLOGY_CENTER(13, R.drawable.tech_centrum),
    FARM(14, R.drawable.farma),
    LIVING_QUARTER_SMALL(15, R.drawable.domecky),
    LIVING_QUARTER_LARGE(16, R.drawable.bytove_domy),
    SCHOOL(17, R.drawable.skola);

    companion object {
        fun fromByte(byteValue: Byte): BuildingType? {
            return entries.find { it.byteValue == byteValue }
        }
    }
}
