# NFCFlasher

NFCFlasher writes and inspects ENAK NFC tags. The global tag protocol is
selected in **Configuration** and is remembered across app restarts.

- **Protocol v2** is the default. Building tags use the external type
  `cz.enak:building` with payload `02 TT`. Command and Wi-Fi records use
  `cz.enak:cmd` and `cz.enak:wifi`, also beginning with version byte `02`.
- **Legacy v1** reproduces the older formats for older mainboards. Building
  records use the well-known type `B` with one payload byte; command and Wi-Fi
  records retain version byte `01`.

Updated mainboards accept only protocol v2. The app's Read screen recognizes
both building formats and reports which protocol was detected.
