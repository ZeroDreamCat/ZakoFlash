# ZakoFlash

Privilege Escalation PoC when seccomp is disabled for Android 10+ with some tools.

### Features

* Backup the first 32MB of mmcblk0 and the frp, boot partitions

* One-click unlock frp

### Build

```bash
git clone https://github.com/ZeroDreamCat/ZakoFlash.git
# On your device
adb shell setenforce 0
./gradlew :app:iR
# Or build in youre Android Studio
```

### Usage

* You must put your device in SELinux permissive mode before using.

1. Install and run ZakoFlash.
2. After that, click the button. This application will back up your boot partition and the first 32 MB of mmcblk0 to the /sdcard/ZakoFlash directory.

For Allwinner devices, you can analyze your device by using the 32 MB empty header at offset 0x2000, which usually contains the SPL file.

- **TOC0 header** is for secure boot, which has AVB. You must unlock the bootloader first!
- **EGON header** is for normal boot, which usually does not have a bootloader lock.

#### FRP Unlock

Modify the last byte of the frp partition to 1, then save it as `/sdcard/ZakoFlash/frp_mod.img`. Click the button to flash it. After that, you can unlock the bootloader on devices that do not have a settings application.

#### Final Step

After patching the boot image, flashing the new boot image with Magisk!

**Execute Shell Commands via Broadcast**

ZakoFlash can execute arbitrary shell commands with root privileges by receiving a broadcast intent. You should open the application to let the application get ready for the root shell.

### Command Format

```bash
adb shell am broadcast -n "io.zerodreamcat.zako.flash/.CommandReceiver" -a io.zerodreamcat.zako.flash.action.EXEC --es cmd "<your command here>"
```

#### Example

##### Execute whoami (should output root):

```bash
adb shell am broadcast -n "io.zerodreamcat.zako.flash/.CommandReceiver" -a io.zerodreamcat.zako.flash.action.EXEC --es cmd "whoami"
```

##### Get Command Output

The command output is broadcasted as a result intent with action io.zerodreamcat.zako.flash.action.RESULT. You can listen for it using a custom broadcast receiver. For quick testing, check logcat with tag ZakoFlash:

```bash
adb logcat -s ZakoFlash:D CommandExecService:D
```

##### Multi-Command

You can chain commands using && or ;:

```bash
--es cmd "ls -l /data && echo 'done'"
```
