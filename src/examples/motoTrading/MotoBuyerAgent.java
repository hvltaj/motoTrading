/*****************************************************************
JADE - Java Agent DEvelopment Framework is a framework to develop 
multi-agent systems in compliance with the FIPA specifications.
Copyright (C) 2000 CSELT S.p.A. 

GNU Lesser General Public License

This library is free software; you can redistribute it and/or
modify it under the terms of the GNU Lesser General Public
License as published by the Free Software Foundation, 
version 2.1 of the License. 

This library is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
Lesser General Public License for more details.

You should have received a copy of the GNU Lesser General Public
License along with this library; if not, write to the
Free Software Foundation, Inc., 59 Temple Place - Suite 330,
Boston, MA  02111-1307, USA.
 *****************************************************************/

package examples.motoTrading;

import jade.core.Agent;
import jade.core.AID;
import jade.core.behaviours.*;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import jade.domain.DFService;
import jade.domain.FIPAException;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;

public class MotoBuyerAgent extends Agent {
	// 
	private String targetMotoTitle;
	private String minSize;
	private String maxSize;
	private String minYear;
	private String maxYear;
    private String request;
	// The list of known seller agents
	private AID[] sellerAgents;

	// Put agent initializations here
	protected void setup() {
		// wiadomosc powitalna
		System.out.println("Hallo! Buyer-agent "+getAID().getName()+" is ready.");

		// Zebranie danych o przedmiocie do kupna od kupujacego z command line
		Object[] args = getArguments();
		if (args != null && args.length > 0) {
			targetMotoTitle = (String) args[0];
            minSize = (String) args[1];
            maxSize = (String) args[2];
            minYear = (String) args[3];
            maxYear = (String) args[4];

            request = targetMotoTitle + ":" +minSize + ":" + maxSize + ":"+ minYear + ":" + maxYear;

			System.out.println("Target moto is "+targetMotoTitle);

			// zachowanie cykliczne co 60 sekund odpytujace DF o liste sprzedajacych z serwisu "moto-trading" oraz wysylajace zapytania o produkt do sprzedajacych
			addBehaviour(new TickerBehaviour(this, 60000) {
				protected void onTick() {
					System.out.println("Trying to buy "+targetMotoTitle+" size ="+minSize + " minYear=" + minYear + " maxYear=" + maxYear);
					// wyslanie zapytania do DF
					DFAgentDescription template = new DFAgentDescription();
					ServiceDescription sd = new ServiceDescription();
					sd.setType("moto-selling");
					template.addServices(sd);
					try {
						DFAgentDescription[] result = DFService.search(myAgent, template); 
						System.out.println("Found the following seller agents:");
						sellerAgents = new AID[result.length];
						for (int i = 0; i < result.length; ++i) {
							sellerAgents[i] = result[i].getName();
							System.out.println(sellerAgents[i].getName());
						}
					}
					catch (FIPAException fe) {
						fe.printStackTrace();
					}

					// wyslanie zapytania do sprzedajacych
					myAgent.addBehaviour(new RequestPerformer());
				}
			} );
		}
		else {
			// zamkniecie agenta w przypadku braku danych
			System.out.println("No target moto title specified");
			doDelete();
		}
	}

	// operacja zamkniecia agenta
	protected void takeDown() {
		// Printout
		System.out.println("Buyer-agent "+getAID().getName()+" terminating.");
	}

	/**
	   Inner class RequestPerformer.
	   Klasa ta wysyla zapytania o przedmiot do sprzedajacych uzyskanych z DF.
	 */
	private class RequestPerformer extends Behaviour {
		private AID bestSeller; // agent z najlepsza cena
		private int bestPrice;  // najlepsza cena
		private int repliesCnt = 0; // liczba odpowiedzi od sprzedajacych
		private MessageTemplate mt; // templatka z wiadomoscia
		private int step = 0;

		public void action() {
			switch (step) {
			case 0:
				// wysyla wiadomosc CFP do wszyskich sprzedajacych
				ACLMessage cfp = new ACLMessage(ACLMessage.CFP);
				for (int i = 0; i < sellerAgents.length; ++i) {
					cfp.addReceiver(sellerAgents[i]);
				} 
				cfp.setContent(request);
				cfp.setConversationId("moto-trade");
				cfp.setReplyWith("cfp"+System.currentTimeMillis()); // unikalna wartosc
				myAgent.send(cfp);
				// przygotowanie templatki
				mt = MessageTemplate.and(MessageTemplate.MatchConversationId("moto-trade"),
						MessageTemplate.MatchInReplyTo(cfp.getReplyWith()));
				step = 1;
				break;
			case 1:
				// odebranie wszystkich propozycji / odmow od agentow sprzedajacych
				ACLMessage reply = myAgent.receive(mt);
				if (reply != null) {
					// uzyskanie odpowiedzi
					if (reply.getPerformative() == ACLMessage.PROPOSE) {
						// to jest oferta
						int price = Integer.parseInt(reply.getContent());
						if (bestSeller == null || price < bestPrice) {
							// w danym momencie najlepsza oferta
							bestPrice = price;
							bestSeller = reply.getSender();
						}
					}
					repliesCnt++;
					if (repliesCnt >= sellerAgents.length) {
						// odebrano wszystkie odpowiedzi. przejscie do nastepnego kroku
						step = 2; 
					}
				}
				else {
					block();
				}
				break;
			case 2:
				// wysyla zadanie kupna do agenta sprzedajacego ktory zaoferowal najlepsza cene
				ACLMessage order = new ACLMessage(ACLMessage.ACCEPT_PROPOSAL);
				order.addReceiver(bestSeller);
				order.setContent(request);
				order.setConversationId("moto-trade");
				order.setReplyWith("order"+System.currentTimeMillis());
				myAgent.send(order);
				// przygotowanie templatki
				mt = MessageTemplate.and(MessageTemplate.MatchConversationId("moto-trade"),
						MessageTemplate.MatchInReplyTo(order.getReplyWith()));
				step = 3;
				break;
			case 3:      
				// odebranie odpowiedzi na zadanie kupna
				reply = myAgent.receive(mt);
				if (reply != null) {
					// odpowiedz
					if (reply.getPerformative() == ACLMessage.INFORM) {
						// kupno sie udalo. pokazanie ceny kupna oraz wyjscie
						System.out.println(request+" successfully purchased from agent "+reply.getSender().getName());
						System.out.println("Price = "+bestPrice);
						myAgent.doDelete();
					}
					else {
                        // kupno sie nie udalo. pokazanie informacji o braku sukcesu
						System.out.println("Attempt failed: requested moto already sold.");
					}

					step = 4;
				}
				else {
					block();
				}
				break;
			}        
		}

		public boolean done() {
			if (step == 2 && bestSeller == null) {
				System.out.println("Attempt failed: "+request+" not available for sale");
			}
			return ((step == 2 && bestSeller == null) || step == 4);
		}
	} 
}
