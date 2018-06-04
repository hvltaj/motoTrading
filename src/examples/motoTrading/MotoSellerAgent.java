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
import jade.core.behaviours.*;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import jade.domain.DFService;
import jade.domain.FIPAException;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;

import java.util.*;

public class MotoSellerAgent extends Agent {
	// --> katalog w ktorym przechowywane beda dane
    private Hashtable catalogue;
	private MotoSellerGui myGui;

	protected void setup() {
		catalogue = new Hashtable();
        // --> Stworzenie GUI w ktorym bedziemy tworzyc produkty
		myGui = new MotoSellerGui(this);
		myGui.showGui();
        
        // -- > Rejestracja serwisu moto-selling do DF by kupujacy mogl znalezc sprzedajacego
		DFAgentDescription dfd = new DFAgentDescription();
		dfd.setName(getAID());
		ServiceDescription sd = new ServiceDescription();
		sd.setType("moto-selling");
		sd.setName("JADE-moto-trading");
		dfd.addServices(sd);
		try {
			DFService.register(this, dfd);
		}
		catch (FIPAException fe) {
			fe.printStackTrace();
		}

		// --> Dodanie zachowania obslugi zapytan o produkt
		addBehaviour(new OfferRequestsServer());

		// dodanie zachowac obslugi ofert kupna
		addBehaviour(new PurchaseOrdersServer());
	}

	// Czyszczenie po sobie
	protected void takeDown() {
		// Derejestracja serwisu z DF
        try {
			DFService.deregister(this);
		}
		catch (FIPAException fe) {
			fe.printStackTrace();
		}
		// Zamkniecie GUI
		myGui.dispose();
		// Printout a dismissal message
		System.out.println("Seller-agent "+getAID().getName()+" terminating.");
	}

	/**
     This is invoked by the GUI when the user adds a new moto for sale
	 */
	public void updateCatalogue(final String title, final String price) {
		addBehaviour(new OneShotBehaviour() {
			public void action() {
				catalogue.put(title, price);
                System.out.println(title+" inserted into catalogue. "+price);
			}
		} );
	}

	/**
	   Inner class OfferRequestsServer.
	   Klasa przetwarza wiadomosc od kupujacego w celu wydobycia istotnych danych. Jesli przedmiot spelmia wymagania wysyla wiadomosc z propozycja ceny.
       Jesli oferowane przez sprzedajacego przedmioty nie spelniaja wymagan lub ich nie ma, klasa wysyla odmowe.
     */
	private class OfferRequestsServer extends CyclicBehaviour {
		public void action() {
			MessageTemplate mt = MessageTemplate.MatchPerformative(ACLMessage.CFP);
			ACLMessage msg = myAgent.receive(mt);
			if (msg != null) {
				// Odebranie wiadomosci z zapytaniem od kupujacego oraz podzielenie wiadomosci na interesujace nas dane
				String zparts = msg.getContent();
                String[] parts = zparts.split(":");
				String title = parts[0];
                int c_minSize = Integer.parseInt(parts[1]);
                int c_maxSize = Integer.parseInt(parts[2]);
                int c_minYear = Integer.parseInt(parts[3]);
                int c_maxYear = Integer.parseInt(parts[4]);

                
                ACLMessage reply = msg.createReply();

				String aaparts = (String)catalogue.get(title);
                //System.out.println("AAAAAAAAAAAAAA: "+ aaparts);
                if (aaparts != null) {
                    String[] s_parts = aaparts.split(":");
                    

                    // Podzial danych z naszego katalogu na interesujace nas wartosci
                    int s_price = Integer.parseInt(s_parts[0]);
                    int s_size = Integer.parseInt(s_parts[1]);
                    int s_year = Integer.parseInt(s_parts[2]);
                
                    // Sprawdzenie czy oferowany przez nas produkt spelnia wyamagania kupujacego
				    if (c_minSize <= s_size && c_maxSize >= s_size && c_minYear <= s_year && c_maxYear >= s_year) {
					// Wyslanie pozytywnej odpowiedzi - oferty wraz z cena jesli nasz produkt spelnia wymagania
					    reply.setPerformative(ACLMessage.PROPOSE);
					    reply.setContent(Integer.toString(s_price));
				    }
                    else {
					// wyslanie odmowy jesli nie spelnia wymagan
					    reply.setPerformative(ACLMessage.REFUSE);
					    reply.setContent("not-available");
				    }
                }
				else {
					// wyslanie odmowy jesli nie posiadamy przemdiotu.
					reply.setPerformative(ACLMessage.REFUSE);
					reply.setContent("not-available");
				}
				myAgent.send(reply);
			}
			else {
				block();
			}
		}
	}  // End of inner class OfferRequestsServer

	/**
	   Inner class PurchaseOrdersServer.
	   Klasa obsluguje wiadomosci o akceptacji oferty od kupujacego. Jesli nikt nie kupil przedmiotu wczesniej wysyla wiadomosc o sprzedazy oraz usuwa przedmiot z wlasnego katalogu.
       W przypadku gdy ktos w miedzyczasie kupil przedmiot wysyla informacje o braku przedmiotu.
     */
	private class PurchaseOrdersServer extends CyclicBehaviour {
		public void action() {
			MessageTemplate mt = MessageTemplate.MatchPerformative(ACLMessage.ACCEPT_PROPOSAL);
			ACLMessage msg = myAgent.receive(mt);
			if (msg != null) {
				// ACCEPT_PROPOSAL Message received. Process it
				String title = msg.getContent();
				ACLMessage reply = msg.createReply();

                String[] ztitle = title.split(":");
                String zztitle = ztitle[0];
                // Usuniecie sprzedanego przedmiotu z wlasnego katalogu
				String price = (String)catalogue.remove(zztitle);
				// Odpowiedz o sprzedazy do agenta kupujacego
                if (price != null) {
					reply.setPerformative(ACLMessage.INFORM);
					System.out.println(title+" sold to agent "+msg.getSender().getName());
				}
				else {
					// Odpowiedz do agenta kupujacego o nieudanej sprzedazy w zwiazku z kupnem przedmiotu przez kogos innego .
					reply.setPerformative(ACLMessage.FAILURE);
					reply.setContent("not-available");
				}
				myAgent.send(reply);
			}
			else {
				block();
			}
		}
	}  // End of inner class OfferRequestsServer
}
