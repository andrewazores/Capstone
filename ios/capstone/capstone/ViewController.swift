//
//  ViewController.swift
//  capstone
//
//  Created by Darren Kitamura on 2014-12-23.
//  Copyright (c) 2014 DKitamura. All rights reserved.
//

import UIKit
import Foundation








class ViewController: UIViewController {
     var mySensor = SensorManager()
    
    @IBAction func accelerometerDataBtn(sender: AnyObject) {
        mySensor.getAccelerationData()
    }
    
    @IBAction func msgA(sender: AnyObject) {
        println("JSON A SHOWN")
        self.jsonMsg = ["posts" : [[ "id" : 1, "message" : "hello world"],[ "id" : 2, "message" : "sample message"]], "new_updates" : false]
    }
    
    @IBAction func msgB(sender: AnyObject) {
        println("JSON B SHOWN")
        self.jsonMsg = ["posts" : [[ "id" : 1, "message" : "hello world"],[ "id" : 2, "message" : "sample message"]], "new_updates" : true]
    
    }
    
   
    var jsonMsg = ["posts" : [[ "id" : 1, "message" : "hello world"],[ "id" : 2, "message" : "sample message"]], "new_updates" : false]
    
    var bonjourServiceBrowser = NSNetServiceBrowser();
    var myBonjourServiceDelegate = myServiceDelegate();
    var myBonjourPublish = NSNetService(domain: "local", type: "_http._tcp", name: "dkCapstone", port: 8060);
   // var myService = ServicePublisher(domain: "local", name: "_http._tcp", type: "dkCapstone")
    
    override func viewDidLoad() {
        super.viewDidLoad()
        
        //If service initialized publish it
        //myService.startService()
        myBonjourPublish.publish()
        
        //Start HTTP Server
        let myHTTPServer = HttpServer()
        myHTTPServer["/hello"] = { request in return .OK(.JSON(self.jsonMsg))
 }
        myHTTPServer.start(listenPort: 8060, error: nil)

        //Start service search
        searchForServices()
        
        
        
    
        // Do any additional setup after loading the view, typically from a nib.
    }

    override func didReceiveMemoryWarning() {
        super.didReceiveMemoryWarning()
        // Dispose of any resources that can be recreated.
    }




func searchForServices() {
    bonjourServiceBrowser.delegate = myBonjourServiceDelegate
    bonjourServiceBrowser.searchForServicesOfType("_http._tcp", inDomain: "local")
   
    
    //bonjourServiceBrowser.scheduleInRunLoop(NSRunLoop.currentRunLoop(), forMode: NSDefaultRunLoopMode)
    
    
}

}