package de.qabel.qabelbox.storage;

import de.qabel.core.crypto.QblECPublicKey;

import java.util.Arrays;

public class BoxExternalReference {
	public boolean isFolder;
	public String name;
	public String url;
	public QblECPublicKey owner;
	public byte[] key;

	public BoxExternalReference(boolean isFolder, String url, String name, QblECPublicKey owner, byte[] key) {
		this.isFolder = isFolder;
		this.name = name;
		this.url = url;
		this.owner = owner;
		this.key = key;
	}

	public String getPrefix() {
		return url.split("/")[0];
	}

	public String getBlock() {
		return url.split("/")[1];
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;

		BoxExternalReference that = (BoxExternalReference) o;

		if (isFolder != that.isFolder) return false;
		if (url != null ? !url.equals(that.url) : that.url != null) return false;
		if (owner != null ? !owner.equals(that.owner) : that.owner != null) return false;
		return Arrays.equals(key, that.key);

	}

	@Override
	public int hashCode() {
		int result = (isFolder ? 1 : 0);
		result = 31 * result + (url != null ? url.hashCode() : 0);
		result = 31 * result + (owner != null ? owner.hashCode() : 0);
		result = 31 * result + (key != null ? Arrays.hashCode(key) : 0);
		return result;
	}

	@Override
	protected BoxExternalReference clone() throws CloneNotSupportedException {
		return new BoxExternalReference(isFolder, url,name,owner,key);
	}
}
