# wediAlert

Watches the fault and fire alarm outputs of a fire panel on a Raspberry Pi and sends SMS through a
GSM modem HAT when either loop opens, and again when it closes. Runs as a systemd service.

The panel interface board (relays, LEDs, 12 V to 5 V) is in `hardware/panel-interface`.

## Configuration

Two files per site (`<env>` is `prod` on the Pi), in `src/main/resources`:

| File | In git | Holds |
|---|---|---|
| `site.<env>.properties` | yes | Settings: fan control, the GPIO of each panel input, SMS texts, periodic test SMS. `site.example.properties` documents them |
| `contacts.<env>.properties` | **no** (ignored) | The phone numbers: `contacts` and `heartbeat.contacts` |

Create the contacts file from its template and put the real numbers in it:

```bash
cp src/main/resources/contacts.example.properties src/main/resources/contacts.prod.properties
```

On the Pi both go in `/etc/wedialert/`; the service passes `-Ddirectory.for.config=/etc/wedialert`.
Without that option, wediAlert reads them from the classpath, for local runs. Older installations with
`contacts` still in the site file keep working, with a warning in the log.

## Build

```bash
mvn package
```

Deploy `target/wediAlerter-*-jar-with-dependencies.jar` as `/opt/wedialert/wediAlert.jar`. The jar
includes whatever is in `src/main/resources`, contacts files too: do not publish a jar built locally.
