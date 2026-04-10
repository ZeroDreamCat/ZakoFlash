# ZakoFlash

A tool that can read boot, header of mmcblk0, frp, and so on. It also includes a function to flash the frp partition to enable OEM unlock settings on devices that do not have a settings application.

However,you must put your device in SELinux permissive mode.

**Usage**

1. Download the zip, unzip it, and install the debug version.
2. After that, click the button. This application will back up your boot partition and the first 32 MB of mmcblk0 to the /sdcard directory.

For Allwinner devices, you can analyze your device by using the 32 MB empty header at offset 0x2000, which usually contains the SPL file.

- **TOC0 header** is for secure boot, which has AVB. You must unlock the bootloader first!
- **EGON header** is for normal boot, which usually does not have a bootloader lock.

**FRP Unlock**

Modify the last byte of the frp partition to 1, then save it as `/sdcard/frp.img`. Click the button to flash it. After that, you can unlock the bootloader on devices that do not have a settings application.

**Final Step**

After patching the boot image, flashing the new boot image with Magisk!

**Execute Shell Commands via Broadcast**

ZakoFlash can execute arbitrary shell commands with root privileges by receiving a broadcast intent. You should open the application to let the application get ready for the root shell.

### Command Format

```bash
am broadcast -n io.zerodreamcat.zako.flash/.CommandReceiver \
  -a io.zerodreamcat.zako.flash.action.EXEC \
  --es cmd "<your command here>"
```

Example

Execute whoami (should output root):

```bash
adb shell am broadcast -n "io.zerodreamcat.zako.flash/.CommandReceiver" -a io.zerodreamcat.zako.flash.action.EXEC --es cmd "whoami"
```

Get Command Output

The command output is broadcasted as a result intent with action io.zerodreamcat.zako.flash.action.RESULT. You can listen for it using a custom broadcast receiver. For quick testing, check logcat with tag ZakoFlash:

```bash
adb logcat -s ZakoFlash:D CommandExecService:D
```

Multi-Command

You can chain commands using && or ;:

```bash
--es cmd "ls -l /data && echo 'done'"
```

**License**

GNU General Public License v3.0 (GPLv3)

