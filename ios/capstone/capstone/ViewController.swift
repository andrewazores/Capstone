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
     let mySensor = SensorManager.sharedInstance
    

  
    
    var bonjourServiceBrowser = NSNetServiceBrowser();
    var myBonjourServiceDelegate = myServiceDelegate();
    //var myBonjourPublish = NSNetService(domain: "local", type: "_http._tcp", name: "CapstoneLocationNSD-", port: 8060);
   // var myService = ServicePublisher(domain: "local", name: "CapstoneLocationNSD-", type: "_http._tcp")
    var myService = ServicePublisher.sharedInstance
    
    
    
    override func viewDidLoad() {
        super.viewDidLoad()
        
        //If service initialized publish it
        myService.startService()
       myService.startWebServer()
        myService.singletonTest()


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
   
    
}

}