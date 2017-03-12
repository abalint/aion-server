package com.aionemu.gameserver.dataholders;

import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.Unmarshaller;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;
import javax.xml.bind.annotation.XmlType;

import com.aionemu.gameserver.model.templates.item.AssemblyItem;

/**
 * @author xTz
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "", propOrder = { "item" })
@XmlRootElement(name = "assembly_items")
public class AssemblyItemsData {

	@XmlElement(required = true)
	protected List<AssemblyItem> item;

	@XmlTransient
	private List<AssemblyItem> items = new ArrayList<>();

	void afterUnmarshal(Unmarshaller unmarshaller, Object parent) {
		for (AssemblyItem template : item) {
			items.add(template);
		}
	}

	public int size() {
		return items.size();
	}

	public AssemblyItem getAssemblyItem(int itemId) {
		for (AssemblyItem assemblyItem : items) {
			if (assemblyItem.getId() == itemId) {
				return assemblyItem;
			}
		}
		return null;
	}
}
