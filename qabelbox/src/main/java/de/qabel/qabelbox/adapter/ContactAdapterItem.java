package de.qabel.qabelbox.adapter;

import java.util.HashSet;

import de.qabel.core.config.Contact;

/**
 * Created by danny on 25.02.16.
 */
public class ContactAdapterItem extends Contact {
	int newMessages = 0;

	public ContactAdapterItem(Contact contact, int newMessages) {
		super(contact.getAlias(),contact.getDropUrls(),contact.getEcPublicKey());
		setEmail((contact.getEmail()));
		setPhone((contact.getPhone()));
		this.newMessages = newMessages;
	}
}
