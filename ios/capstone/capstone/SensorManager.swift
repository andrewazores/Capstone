//
//  SensorManager.swift
//  capstone
//
//  Created by Darren Kitamura on 2014-12-28.
//  Copyright (c) 2014 DKitamura. All rights reserved.
//

import Foundation
import CoreMotion
import CoreLocation

class SensorManager: NSObject, CLLocationManagerDelegate, NSObjectProtocol {
    
    let mySensorManager: CMMotionManager
    let myAltimeterManager: CMAltimeter
    let myLocationManager: CLLocationManager
    var accelX:Double = 0.0
    var accelY:Double = 0.0
    var accelZ:Double = 0.0
    var rotX:Double = 0.0
    var rotY:Double = 0.0
    var rotZ:Double = 0.0
    var altitude:Float = 0.0
    var pressure: Float = 0.0
    var latitude: Double = 0.0
    var longitude: Double = 0.0
    
    class var sharedInstance: SensorManager{
        
        struct Static {
            static var instance: SensorManager?
            static var token: dispatch_once_t = 0
        }
        
        dispatch_once(&Static.token) {
            Static.instance = SensorManager()
        }
        return Static.instance!
    }
    
   override init () {
        mySensorManager = CMMotionManager()
        myAltimeterManager = CMAltimeter()
        myLocationManager = CLLocationManager()
        myLocationManager.requestWhenInUseAuthorization()
    myLocationManager.requestAlwaysAuthorization()
    


        mySensorManager.deviceMotionUpdateInterval = 0.01
        mySensorManager.accelerometerUpdateInterval = 0.01
        mySensorManager.gyroUpdateInterval = 0.01
        
  
     
        super.init()
    
    if(CLLocationManager.locationServicesEnabled()) {
        println("Location Enabled")
               myLocationManager.delegate = self
     myLocationManager.desiredAccuracy = kCLLocationAccuracyBest
            myLocationManager.startUpdatingLocation()

    }
    myAltimeterManager.startRelativeAltitudeUpdatesToQueue(NSOperationQueue.currentQueue(), withHandler: {(altimeterData: CMAltitudeData!, error:NSError!)in
        self.outputAltimeterData(altimeterData)
        if(error != nil) {
            println("\(error)")
        }
    })
    
    mySensorManager.startAccelerometerUpdatesToQueue(NSOperationQueue.currentQueue(), withHandler: {(accelerometerData: CMAccelerometerData!, error:NSError!)in
        self.outputAccelerationData(accelerometerData.acceleration)
        if (error != nil)
        {
            println("\(error)")
        }
    })
    
    mySensorManager.startGyroUpdatesToQueue(NSOperationQueue.currentQueue(), withHandler: {(gyroData: CMGyroData!, error: NSError!)in
        self.outputRotationData(gyroData.rotationRate)
        if (error != nil)
        {
            println("\(error)")
        }
    })
    
    }
    
    
    func locationManager(manager: CLLocationManager!, didUpdateLocations locations: [AnyObject]!) {
        
        var locValue:CLLocationCoordinate2D = manager.location.coordinate
        
        println("locations = \(locValue.latitude) \(locValue.longitude)")
        latitude = locValue.latitude as Double!
        longitude = locValue.longitude as Double!
        
    }
    


    
    func outputAccelerationData(acceleration:CMAcceleration) {
        accelX = acceleration.x
        accelY = acceleration.y
        accelZ = acceleration.z
        
    }
    
    func outputRotationData(rotation: CMRotationRate) {
       // println("Rotation X: \(rotX)")
        rotX = rotation.x
        rotY = rotation.y
        rotZ = rotation.z
    }
    
    func outputAltimeterData(altimiter: CMAltitudeData) {
        altitude = altimiter.relativeAltitude.floatValue
        pressure = altimiter.pressure.floatValue
    }
    
    
    func getAccelerationData() -> (Double, Double, Double) {
        return(accelX, accelY, accelZ)
    }
    
    func getRotationData() -> (Double, Double, Double) {
        return(rotX, rotY, rotZ)
    }
    
}
