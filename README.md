# Cordova-Plugin-Bluetooth-Printer
A cordova plugin for bluetooth printer for android platform, which support text, image and POS printing.

##Support
- Print Text (Eng)
- Print cyrillic text
- POS Commands
- Image Printing 


##Install
Using the Cordova CLI and NPM, run:

```
cordova plugin add https://github.com/Navispirit/Cordova-Plugin-Bluetooth-Printer.git
```



##Usage
Get list of paired bluetooth printers

```
BTPrinter.list(function(data){
        console.log("Success");
        console.log(data); \\list of printer in data array
    },function(err){
        console.log("Error");
        console.log(err);
    })
```


Connect printer

```
BTPrinter.connect(function(data){
	console.log("Success");
	console.log(data)
},function(err){
	console.log("Error");
	console.log(err)
}, "PrinterName")
```


Disconnect printer

```
BTPrinter.disconnect(function(data){
	console.log("Success");
	console.log(data)
},function(err){
	console.log("Error");
	console.log(err)
}, "PrinterName")
```


Print cyrillic text

```
BTPrinter.printCyrillic(function(data){
    console.log("Success");
    console.log(data);
},function(err){
    console.log("Error");
    console.log(err);
}, "String to Print")
```


Print text

```
BTPrinter.printText(function(data){
    console.log("Success");
    console.log(data)
},function(err){
    console.log("Error");
    console.log(err)
}, "String to Print")
```


Print image

```
BTPrinter.printImage(function(data){
    console.log("Success");
    console.log(data)
},function(err){
    console.log("Error");
    console.log(err)
}, "Base64 String of Image")
```


POS printing

```
BTPrinter.printPOSCommand(function(data){
    console.log("Success");
    console.log(data)
},function(err){
    console.log("Error");
    console.log(err)
}, "0C")
//OC is a POS command for page feed
```