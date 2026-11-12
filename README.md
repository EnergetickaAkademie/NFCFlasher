# NFCFlasher

NFCFlasher writes and inspects ENAK NFC tags. The global tag protocol is
selected in **Configuration** and is remembered across app restarts.

- **Protocol v2** is the default. Building tags use the external type
  `cz.enak:building` with payload `02 TT`. Command and Wi-Fi records use
  `cz.enak:cmd` and `cz.enak:wifi`, also beginning with version byte `02`.
  The Provision screen can also prepare a debug card (`cz.enak:cmd`, `02 02`)
  or bulldozer card (`cz.enak:cmd`, `02 03`).
- **Legacy v1** reproduces the older formats for older mainboards. Building
  records use the well-known type `B` with one payload byte; command and Wi-Fi
  records retain version byte `01`.

Updated mainboards accept only protocol v2. The app's Read screen recognizes
both building formats plus reset, debug, bulldozer, and Wi-Fi provisioning
records, and reports which protocol was detected. Other NDEF records are shown
with their record type and raw payload instead of being reported as invalid
building tags.
