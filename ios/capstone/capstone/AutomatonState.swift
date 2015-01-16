//
//  AutomatonState.swift
//  capstone
//
//  Created by Darren Kitamura on 2015-01-15.
//  Copyright (c) 2015 DKitamura. All rights reserved.
//

import Foundation


class AutomatonState {
    var stateName: String
    var stateType: Automaton.Evaluation
    
    init(state: AutomatonState) {
        self.stateName = state.stateName
        self.stateType = state.stateType
    }
    
    func toString() -> String {
        return "AutomatonState{stateName='\(stateName)', stateType=\(stateType)}"
    }
    //TODO Override Equals...
    
    
    //TODO Hashcode
    func hashCode() -> Int {
        var result: Int
        
        return 0
    }
    
}