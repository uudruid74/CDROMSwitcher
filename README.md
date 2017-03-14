# CDROMSwitcher

Tap an ISO file, now your phone is a CDROM with that ISO as the CD!
Tap a new ISO and the USB is torn down and restarted with the new disk.

This is tested only on a OnePlus 3T.   It will only work if you see
a CDROM (usb mass storage device) when you plug your phone into your
computer.  If you don't see it, your phone isn't compatible, so
please don't try.

WARNING:  This app uses root and needs to modify one file on your
Android /system partition.  Changing files on /system means you
can no longer do binary-patch style OTAs, only full-ROMs!  You
have been warned!  The change is that /system/etc/usb_drivers.iso
is changed to a symlink.  The original is renamed .orig.iso

DISCLAIMER!  I take no responsibility for this software.  If you
install it and it causes your phone to melt, start a thermonuclear
war, or it rapes your dog ... I am NOT responsible!

