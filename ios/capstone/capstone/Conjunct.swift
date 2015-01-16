//
//  Conjunct.swift
//  capstone
//
//  Created by Darren Kitamura on 2015-01-15.
//  Copyright (c) 2015 DKitamura. All rights reserved.
//

import Foundation


class Conjunct {
    
      enum Evaluation {
        case TRUE
        case FALSE
        case NONE
    }
    
    //Getter
    final private var ownerProcess: NetworkPeerIdentifier
    
    final private var expression: BooleanExpressionTree
    
    init(ownerProcess: NetworkPeerIdentifier, expression: BooleanExpressionTree) {
        self.ownerProcess = ownerProcess
        self.expression = expression
    }
    
    public Evaluation evaluate(state: ProcessState) {
        if(ownerProcess != state.getId()) {
            return Evaluation.NONE
        }
        return expression.evaluate(state)
    }
    
}
