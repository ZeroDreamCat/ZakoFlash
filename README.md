**ZakoFlash**

A tool that can read boot, recovery, header of mmcblk0, frp, and so on. It also includes a function to flash the frp partition to enable OEM unlock settings on devices that do not have a settings application.

However,you must put your device in SELinux permissive mode.

**Usage**

1. Download the zip, unzip it, and install the debug version.
2. After that, click the button. This application will back up your boot partition and the first 8 MB of mmcblk0 to the /sdcard directory.

For Allwinner devices, you can analyze your device by using the 32 MB empty header at offset 0x2000, which usually contains the SPL file.

- **TOC0 header** is for secure boot, which has AVB. You must unlock the bootloader first!
- **EGON header** is for normal boot, which usually does not have a bootloader lock.

**FRP Unlock**

Modify the last byte of the frp partition to 1, then save it as `/sdcard/frp.img`. Click the button to flash it. After that, you can unlock the bootloader on devices that do not have a settings application.

**Final Step**

Now flash the new boot image with Magisk!

**License**

GNU General Public License v3.0 (GPLv3)

