# ZakoFlash 

A tool which can read boot, recovery,header of mmcblk0,frp and so on.Also conclude a function which can flash frp to open OEM umlock settings on devices which hasn't settings application.

## Usage

1. Download zip,unzip and install debug version.

2. After that,click the buttom,this application will backup your boot and 8m head of mmcblk0 to sdcard.

for allwinner device,you can analysis your device through 32m empty header at 0x2000 offset which usually contains spl file.

Toc0 header is secure boot which has avb,and you must unlock BOOTLOADER first!Egon is normal boot which usually doesn't has bootloader lock.

3. Modify the last byte of frp partition to 1. And put it in /sdcard/frp.img,click the bottom to flash it and now you can unlock bootloader on devices which doesn't has seetings application.

4. Now,flash the new boot image with Magisk!


## License

GNU V3.0
