#!/bin/bash
java -cp lib/moto.jar:lib/jade.jar  jade.Boot -gui -agents 'buyer:examples.motoTrading.MotoBuyerAgent(yamaha,600,800,2014,2016);seller1:examples.motoTrading.MotoSellerAgent;seller2:examples.motoTrading.MotoSellerAgent'
