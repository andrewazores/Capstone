//
//  SensorManager.swift
//  capstone
//
//  Created by Darren Kitamura on 2014-12-28.
//  Copyright (c) 2014 DKitamura. All rights reserved.
//

import Foundation
import CoreMotion

class SensorManager {
    
    let mySensorManager: CMMotionManager
    let myAltimeterManager: CMAltimeter
    var accelX:Double = 0.0
    var accelY:Double = 0.0
    var accelZ:Double = 0.0
    var rotX:Double = 0.0
    var rotY:Double = 0.0
    var rotZ:Double = 0.0
    var altitude:Float = 0.0
    
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
    
    init () {
        mySensorManager = CMMotionManager()
        myAltimeterManager = CMAltimeter()
        
        mySensorManager.deviceMotionUpdateInterval = 0.01
        mySensorManager.accelerometerUpdateInterval = 0.01
        mySensorManager.gyroUpdateInterval = 0.01
        
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
    
    func outputAccelerationData(acceleration:CMAcceleration) {
        println("Acceleration X: \(accelX)")
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
    }
    
    
    func getAccelerationData() -> (Double, Double, Double) {
        println("X: \(accelX) Y: \(accelY) Z: \(accelZ)")
        return(accelX, accelY, accelZ)
    }
    
    func getRotationData() -> (Double, Double, Double) {
        return(rotX, rotY, rotZ)
    }
    
}
