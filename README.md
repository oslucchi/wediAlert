# wediAlert

Watches the fault and fire alarm outputs of a fire panel on a Raspberry Pi and sends SMS through a
GSM modem HAT when either loop opens, and again when it closes. Runs as a systemd service.

The panel interface board (relays, LEDs, 12 V to 5 V) is in `hardware/panel-interface`.

## Configuration

The site file holds the phone numbers, so it is not in the repository. Create it from the example:

```bash
cp src/main/resources/site.example.properties site.prod.properties
```

Set `contacts`, then install it on the Pi as `/etc/wedialert/site.prod.properties`; the service passes
`-Ddirectory.for.config=/etc/wedialert`. Without that option, wediAlert reads
`site.<env>.properties` from the classpath (`src/main/resources`, ignored by git) for local runs.

The example file documents every setting: contacts, fan control, the GPIO of each panel input, the SMS
texts and the periodic test SMS.

## Build

```bash
mvn package
```

Deploy `target/wediAlerter-*-jar-with-dependencies.jar` as `/opt/wedialert/wediAlert.jar`.
