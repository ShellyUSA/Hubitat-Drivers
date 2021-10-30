# Hubitat-Drivers
Device Handlers To Use Shelly products With Hubitat

Thanks to Scott Grayban for his time and effort developing these device handlers for the Shelly-Hubitat community.

Shelly smart home products are available for European customers at https://shop.shelly.cloud/ and for US customers at https://shopusa.shelly.cloud/

* Shelly-Bulb.groovy - for use with the original Shelly Bulb
* Shelly-Duo.groovy - for use with the Shelly Duo bulb
* Shelly-HT.groovy - for use with the Shelly HT and Flood sensors, requires the use of an MQTT broker
* Shelly-RGBW-White.groovy - for use with the Shelly RGBW2 using White firmware
* Shelly=RGBW.groovy - for use with the Shelly RGW2 using Color firmware
* Shelly-Vintage.groovy - for use with the Shelly Vintage bulbs (compatible with all models)
* Shelly-as-a-Switch.groovy - for use with Shelly 1, Shelly 1PM, Shelly 2, Shelly 2.5, and Shelly Plug/Plug S/Plug US products
* Shelly2-as-Roller-Shutter - for use with Shelly 2 or Shelly 2.5 in Roller Shutter mode
* Shelly-Gas.groovy - for use with the Shelly Gas device
* Shelly-Contact.groovy - for use with the Shelly Window/Door sensor

Device Handler Installation instructions:

1. Click the appropriate Groovy file for your device type.
2. Click the Raw button 
3. Select all text, then copy
4. Navigate to your Hubitat's IP address in your web browser
5. Click Drivers Code
6. Click the New Driver button
7. Paste the code copied in step 3
8. Click Save

Using Device Handlers With Shelly products

Different Shelly devices require varied settings. Device handlers for Shelly sensors require the use of an MQTT broker. Shelly modules with multiple relays or channels - Shelly 2.5, Shelly 4Pro, & Shelly RGBW2, for example - require that you select the specific channel for the device handler to work with.

The instructions assume you've already added the device handler using the steps above.

1. Navigate to your Hubitat's IP address in your web browser
2. Click Devices
3. Click the Add Virtual Device button
4. Enter a Device Name and Device Label for the Shelly module
5. Select  the appropriate device handler from the "User" section in the "Type" menu
6. Click the "Save Device" button
7. Enter valid settings for any field marked with an asterisk. For example: Shelly As A Switch, for use with a Shelly 2.5 relay. Enter valid values for IP address and Relay Channel (choices will be o or 1 for the Shelly 2.5).  *Note:* For the Shelly 2.5, you will set up a second instance of the device handler for the second relay channel.
8. Click Save Preferences
9. When "Current States" populates with valid data for your device, the device handler is configured properly.

##Shelly-as-a-switch notes:

Please note that for using the external temp monitor add-on as a switch, you must use the black and yellow wire.

For optimum results, please enable MakerAPI App in hubitat, add the shelly device. (this will enable live update of the external switch state in hubitat)

When you have the MakerAPI URL string for "refresh" of your shelly device, go into your shelly's webinterface --> I/O URL Actions --> EXTERNAL_SWITCH ON & EXTERNAL_SWITCH OFF and add the "refresh request url" (Should look something like the URL below) - add the same URL on both.

The below URL must be adjusted to what you get from the MakerAPI App<br>
http://[HUBITAT-IP-ADDRESS]/apps/api/[API_ID]/devices/[DEVICEID]/refresh?access_token=[TOKENI_ID]
