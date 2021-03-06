/**
 *                            License
 * THE WORK (AS DEFINED BELOW) IS PROVIDED UNDER THE TERMS OF THIS  
 * CREATIVE COMMONS PUBLIC LICENSE ("CCPL" OR "LICENSE"). 
 * THE WORK IS PROTECTED BY COPYRIGHT AND/OR OTHER APPLICABLE LAW.  
 * ANY USE OF THE WORK OTHER THAN AS AUTHORIZED UNDER THIS LICENSE OR  
 * COPYRIGHT LAW IS PROHIBITED.
 * 
 * BY EXERCISING ANY RIGHTS TO THE WORK PROVIDED HERE, YOU ACCEPT AND  
 * AGREE TO BE BOUND BY THE TERMS OF THIS LICENSE. TO THE EXTENT THIS LICENSE  
 * MAY BE CONSIDERED TO BE A CONTRACT, THE LICENSOR GRANTS YOU THE RIGHTS CONTAINED 
 * HERE IN CONSIDERATION OF YOUR ACCEPTANCE OF SUCH TERMS AND CONDITIONS.
 * 
 */
package l1j.server.server.clientpackets;

import java.util.List;
import java.util.logging.Logger;

import l1j.server.server.ClientThread;
import l1j.server.server.datatables.NpcTable;
import l1j.server.server.datatables.PetTable;
import l1j.server.server.datatables.ShopTable;
import l1j.server.server.model.L1Clan;
import l1j.server.server.model.L1Inventory;
import l1j.server.server.model.L1Object;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.L1DollInstance;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.Instance.L1PetInstance;
import l1j.server.server.model.identity.L1ItemId;
import l1j.server.server.model.shop.L1Shop;
import l1j.server.server.model.shop.L1ShopBuyOrderList;
import l1j.server.server.model.shop.L1ShopSellOrderList;
import l1j.server.server.serverpackets.S_ServerMessage;
import l1j.server.server.templates.L1Npc;
import l1j.server.server.templates.L1Pet;
import l1j.server.server.templates.L1PrivateShopBuyList;
import l1j.server.server.templates.L1PrivateShopSellList;

import l1j.server.server.Account;
import l1j.server.server.datatables.IpTable;
import l1j.server.server.serverpackets.S_Disconnect; 
import java.util.logging.Level; 

import l1j.server.server.log.LogDwarfIn;
import l1j.server.server.log.LogDwarfOut;

/**
 * TODO ??????????????? ???????????????????????????????????????????????????
 */
public class C_Result extends ClientBasePacket {

	private static final String C_RESULT = "[C] C_Result";
	private static Logger _log = Logger.getLogger(C_Result.class.getName()); 
	
	// TODO: somewhere more accessible, since we use this other places.
	public void ban(final L1PcInstance player) {
  	    Account.ban(player.getAccountName());
  	    IpTable.getInstance().banIp(player.getNetConnection().getIp());
  	    player.sendPackets(new S_Disconnect());
	} 

	public C_Result(byte abyte0[], ClientThread clientthread) throws Exception {
		super(abyte0);
		
		L1PcInstance pc = clientthread.getActiveChar();
		if (pc == null) {
			return;
		}
		
		int npcObjectId = readD();
		int resultType = readC();
		int size = readH();
		int level = pc.getLevel();

		int npcId = 0;
		String npcImpl = "";
		boolean isPrivateShop = false;
		boolean tradable = true;
		L1Object findObject = L1World.getInstance().findObject(npcObjectId);
		if (findObject != null) {
			int diffLocX = Math.abs(pc.getX() - findObject.getX());
			int diffLocY = Math.abs(pc.getY() - findObject.getY());
			// 3????????????????????????????????????
			if ((diffLocX > 3) || (diffLocY > 3)) {
				return;
			}
			if (findObject instanceof L1NpcInstance) {
				L1NpcInstance targetNpc = (L1NpcInstance) findObject;
				npcId = targetNpc.getNpcTemplate().get_npcId();
				npcImpl = targetNpc.getNpcTemplate().getImpl();
			} else if (findObject instanceof L1PcInstance) {
				isPrivateShop = true;
			}
		}

		if ((resultType == 0) && (size != 0)
				&& npcImpl.equalsIgnoreCase("L1Merchant")) { // ?????????
			L1Shop shop = ShopTable.getInstance().get(npcId);
			L1ShopBuyOrderList orderList = shop.newBuyOrderList();
			for (int i = 0; i < size; i++) {
				orderList.add(readD(), readD());
			}
			shop.sellItems(pc, orderList);
		} else if ((resultType == 1) && (size != 0)
				&& npcImpl.equalsIgnoreCase("L1Merchant")) { // ?????????
			L1Shop shop = ShopTable.getInstance().get(npcId);
			L1ShopSellOrderList orderList = shop.newSellOrderList(pc);
			for (int i = 0; i < size; i++) {
				orderList.add(readD(), readD());
			}
			shop.buyItems(orderList);
		} else if ((resultType == 2) && (size != 0)
				&& npcImpl.equalsIgnoreCase("L1Dwarf") && (level >= 5)) { // ???????????????
			int objectId, count;
			for (int i = 0; i < size; i++) {
				tradable = true;
				objectId = readD();
				count = readD();
				L1Object object = pc.getInventory().getItem(objectId);
				L1ItemInstance item = (L1ItemInstance) object;
				int item_count_before = item.getCount(); 
				int item_count_after = 0;  
				if (!item.getItem().isTradable()) {
					tradable = false;
					pc.sendPackets(new S_ServerMessage(210, item.getItem()
							.getName())); // \f1%0??????????????????????????????????????????????????????????????????
				}
				for (L1NpcInstance petNpc : pc.getPetList().values()) {
					if (petNpc instanceof L1PetInstance) {
						L1PetInstance pet = (L1PetInstance) petNpc;
						if (item.getId() == pet.getItemObjId()) {
							tradable = false;
							// \f1%0??????????????????????????????????????????????????????????????????
							pc.sendPackets(new S_ServerMessage(210, item
									.getItem().getName()));
							break;
						}
					}
				}
				for (L1DollInstance doll : pc.getDollList().values()) {
					if (item.getId() == doll.getItemObjId()) {
						tradable = false;
						pc.sendPackets(new S_ServerMessage(1181)); // ?????????????????????????????????????????????????????????
						break;
					}
				}
				if (pc.getDwarfInventory().checkAddItemToWarehouse(item, count,
						L1Inventory.WAREHOUSE_TYPE_PERSONAL) == L1Inventory.SIZE_OVER) {
					pc.sendPackets(new S_ServerMessage(75)); // \f1??????????????????????????????????????????????????????
					break;
				}
				if (count > item.getCount()) {
						_log.log(Level.SEVERE, pc.getName() + " tried to store " +
						count + " of " + objectId + " but server has " +
						item.getCount() + ".");
						ban(pc);
						return;
				} 
				if (tradable) {
					pc.getInventory().tradeItem(objectId, count,
							pc.getDwarfInventory());
					pc.turnOnOffLight();
					L1ItemInstance pcitem = pc.getInventory().getItem(objectId); 
					if (pcitem != null) { 
						item_count_after = pcitem.getCount(); 
					} 
					LogDwarfIn ldi = new LogDwarfIn(); 
					ldi.storeLogDwarfIn(pc, item, item_count_before, item_count_after, count); 
				}
			}

			// ??????????????????????????????, ????????????????????????????????????????????????????????????????????????
			pc.saveInventory();
		} else if ((resultType == 3) && (size != 0)
				&& npcImpl.equalsIgnoreCase("L1Dwarf") && (level >= 5)) { // ?????????????????????
			int objectId, count;
			L1ItemInstance item;
			for (int i = 0; i < size; i++) {
				objectId = readD();
				count = readD();
				item = pc.getDwarfInventory().getItem(objectId);
				int item_count_before = item.getCount(); 
				int item_count_after = 0;
				if (count > item.getCount()) {
					_log.log(Level.SEVERE, pc.getName() + " tried to retrieve " +
							count + " of " + objectId + " but server has " +
							item.getCount() + ".");
					ban(pc);
					return;
				}
				if (pc.getInventory().checkAddItem(item, count) == L1Inventory.OK) // ?????????????????????
				{
					if (pc.getInventory().consumeItem(L1ItemId.ADENA, 30)) {
						pc.getDwarfInventory().tradeItem(item, count,
								pc.getInventory());
						L1ItemInstance dwitem = pc.getDwarfInventory().getItem(objectId); 
						if (dwitem != null) { 
							item_count_after = dwitem.getCount(); 
						} 
						LogDwarfOut ldo = new LogDwarfOut(); 
						ldo.storeLogDwarfOut(pc, item, item_count_before, item_count_after, count);
					} else {
						pc.sendPackets(new S_ServerMessage(189)); // \f1????????????????????????????????????
						break;
					}
				} else {
					pc.sendPackets(new S_ServerMessage(270)); // \f1?????????????????????????????????????????????????????????
					break;
				}
			}
		} else if ((resultType == 4) && (size != 0)
				&& npcImpl.equalsIgnoreCase("L1Dwarf") && (level >= 5)) { // ?????????????????????
			int objectId, count;
			if (pc.getClanid() != 0) { // ?????????
				for (int i = 0; i < size; i++) {
					tradable = true;
					objectId = readD();
					count = readD();
					L1Clan clan = L1World.getInstance().getClan(
							pc.getClanname());
					L1Object object = pc.getInventory().getItem(objectId);
					L1ItemInstance item = (L1ItemInstance) object;
					if (count > item.getCount()) {
						_log.log(Level.SEVERE, pc.getName() + " tried to (clan) store " +
								count + " of " + objectId + " but server has " +
								item.getCount() + ".");
						ban(pc);
						return;
					}
					if (clan != null) {
						if (!item.getItem().isTradable()) {
							tradable = false;
							pc.sendPackets(new S_ServerMessage(210, item
									.getItem().getName())); // \f1%0??????????????????????????????????????????????????????????????????
						}
						if (item.getBless() >= 128) { // ??????????????????
							tradable = false;
							pc.sendPackets(new S_ServerMessage(210, item
									.getItem().getName())); // \f1%0??????????????????????????????????????????????????????????????????
						}
						for (L1NpcInstance petNpc : pc.getPetList().values()) {
							if (petNpc instanceof L1PetInstance) {
								L1PetInstance pet = (L1PetInstance) petNpc;
								if (item.getId() == pet.getItemObjId()) {
									tradable = false;
									// \f1%0??????????????????????????????????????????????????????????????????
									pc.sendPackets(new S_ServerMessage(210,
											item.getItem().getName()));
									break;
								}
							}
						}
						for (L1DollInstance doll : pc.getDollList().values()) {
							if (item.getId() == doll.getItemObjId()) {
								tradable = false;
								pc.sendPackets(new S_ServerMessage(1181)); // ?????????????????????????????????????????????????????????
								break;

							}
						}
						if (clan.getDwarfForClanInventory()
								.checkAddItemToWarehouse(item, count,
										L1Inventory.WAREHOUSE_TYPE_CLAN) == L1Inventory.SIZE_OVER) {
							pc.sendPackets(new S_ServerMessage(75)); // \f1??????????????????????????????????????????????????????
							break;
						}
						if (tradable) {
							pc.getInventory().tradeItem(objectId, count,
									clan.getDwarfForClanInventory());
							pc.turnOnOffLight();
						}
					}
				}

				// ??????????????????????????????, ????????????????????????????????????????????????????????????????????????
				pc.saveInventory();
			} else {
				pc.sendPackets(new S_ServerMessage(208)); // \f1???????????????????????????????????????????????????????????????????????????????????????
			}
		} else if ((resultType == 5) && (size != 0)
				&& npcImpl.equalsIgnoreCase("L1Dwarf") && (level >= 5)) { // ?????????????????????????????????
			int objectId, count;
			L1ItemInstance item;

			L1Clan clan = L1World.getInstance().getClan(pc.getClanname());
			if (clan != null) {
				for (int i = 0; i < size; i++) {
					objectId = readD();
					count = readD();
					item = clan.getDwarfForClanInventory().getItem(objectId);
					if (count > item.getCount()) {
						_log.log(Level.SEVERE, pc.getName() + " tried to (clan) retrieve " +
								count + " of " + objectId + " but server has " +
								item.getCount() + ".");
						ban(pc);
						return;
					}
					if (pc.getInventory().checkAddItem(item, count) == L1Inventory.OK) { // ?????????????????????????????????????????????
						if (pc.getInventory().consumeItem(L1ItemId.ADENA, 30)) {
							clan.getDwarfForClanInventory().tradeItem(item,
									count, pc.getInventory());
						} else {
							pc.sendPackets(new S_ServerMessage(189)); // \f1????????????????????????????????????
							break;
						}
					} else {
						pc.sendPackets(new S_ServerMessage(270)); // \f1?????????????????????????????????????????????????????????
						break;
					}
				}
				clan.setWarehouseUsingChar(0); // ????????????????????????????????????
			}
		} else if ((resultType == 5) && (size == 0)
				&& npcImpl.equalsIgnoreCase("L1Dwarf")) { // ???????????????????????????????????????Cancel???????????????ESC??????
			L1Clan clan = L1World.getInstance().getClan(pc.getClanname());
			if (clan != null) {
				clan.setWarehouseUsingChar(0); // ????????????????????????????????????
			}
		} else if ((resultType == 8) && (size != 0)
				&& npcImpl.equalsIgnoreCase("L1Dwarf") && (level >= 5)
				&& pc.isElf()) { // ?????????????????????????????????
			int objectId, count;
			for (int i = 0; i < size; i++) {
				tradable = true;
				objectId = readD();
				count = readD();
				L1Object object = pc.getInventory().getItem(objectId);
				L1ItemInstance item = (L1ItemInstance) object;
				if (count > item.getCount()) {
					_log.log(Level.SEVERE, pc.getName() + " tried to (elf) store " +
							count + " of " + objectId + " but server has " +
							item.getCount() + ".");
					ban(pc);
					return;
				}
				if (!item.getItem().isTradable()) {
					tradable = false;
					pc.sendPackets(new S_ServerMessage(210, item.getItem()
							.getName())); // \f1%0??????????????????????????????????????????????????????????????????
				}
				for (L1NpcInstance petNpc : pc.getPetList().values()) {
					if (petNpc instanceof L1PetInstance) {
						L1PetInstance pet = (L1PetInstance) petNpc;
						if (item.getId() == pet.getItemObjId()) {
							tradable = false;
							// \f1%0??????????????????????????????????????????????????????????????????
							pc.sendPackets(new S_ServerMessage(210, item
									.getItem().getName()));
							break;
						}
					}
				}
				for (L1DollInstance doll : pc.getDollList().values()) {
					if (item.getId() == doll.getItemObjId()) {
						tradable = false;
						pc.sendPackets(new S_ServerMessage(1181)); // ?????????????????????????????????????????????????????????
						break;
					}
				}
				if (pc.getDwarfForElfInventory().checkAddItemToWarehouse(item,
						count, L1Inventory.WAREHOUSE_TYPE_PERSONAL) == L1Inventory.SIZE_OVER) {
					pc.sendPackets(new S_ServerMessage(75)); // \f1??????????????????????????????????????????????????????
					break;
				}
				if (tradable) {
					pc.getInventory().tradeItem(objectId, count,
							pc.getDwarfForElfInventory());
					pc.turnOnOffLight();
				}
			}

			// ??????????????????????????????, ????????????????????????????????????????????????????????????????????????
			pc.saveInventory();
		} else if ((resultType == 9) && (size != 0)
				&& npcImpl.equalsIgnoreCase("L1Dwarf") && (level >= 5)
				&& pc.isElf()) { // ??????????????????????????????????????????
			int objectId, count;
			L1ItemInstance item;
			for (int i = 0; i < size; i++) {
				objectId = readD();
				count = readD();
				item = pc.getDwarfForElfInventory().getItem(objectId);
				if (count > item.getCount()) {
					_log.log(Level.SEVERE, pc.getName() + " tried to (elf) retrieve " +
							count + " of " + objectId + " but server has " +
							item.getCount() + ".");
					ban(pc);
					return;
				}
				if (pc.getInventory().checkAddItem(item, count) == L1Inventory.OK) { // ?????????????????????????????????????????????
					if (pc.getInventory().consumeItem(40494, 2)) { // ????????????
						pc.getDwarfForElfInventory().tradeItem(item, count,
								pc.getInventory());
					} else {
						pc.sendPackets(new S_ServerMessage(337, "$767")); // \f1%0???????????????????????????
						break;
					}
				} else {
					pc.sendPackets(new S_ServerMessage(270)); // \f1?????????????????????????????????????????????????????????
					break;
				}
			}
		} else if ((resultType == 0) && (size != 0) && isPrivateShop) { // ????????????????????????????????????
			if (findObject == null) {
				return;
			}
			if (!(findObject instanceof L1PcInstance)) {
				return;
			}
			L1PcInstance targetPc = (L1PcInstance) findObject;

			int order;
			int count;
			int price;
			List<L1PrivateShopSellList> sellList;
			L1PrivateShopSellList pssl;
			int itemObjectId;
			int sellPrice;
			int sellTotalCount;
			int sellCount;
			L1ItemInstance item;
			boolean[] isRemoveFromList = new boolean[8];

			if (targetPc.isTradingInPrivateShop()) {
				return;
			}
			sellList = targetPc.getSellList();
			synchronized (sellList) {
				// ?????????????????????????????????????????????????????????????????????????????????
				if (pc.getPartnersPrivateShopItemCount() != sellList.size()) {
					return;
				}
				targetPc.setTradingInPrivateShop(true);

				for (int i = 0; i < size; i++) { // ?????????????????????
					order = readD();
					count = readD();
					pssl = sellList.get(order);
					itemObjectId = pssl.getItemObjectId();
					sellPrice = pssl.getSellPrice();
					sellTotalCount = pssl.getSellTotalCount(); // ?????????????????????
					sellCount = pssl.getSellCount(); // ???????????????
					item = targetPc.getInventory().getItem(itemObjectId);
					if (item == null) {
						continue;
					}
					if (count > sellTotalCount - sellCount) {
						count = sellTotalCount - sellCount;
					}
					if (count == 0) {
						continue;
					}

					if (pc.getInventory().checkAddItem(item, count) == L1Inventory.OK) { // ?????????????????????????????????????????????
						for (int j = 0; j < count; j++) { // ????????????????????????????????????
							if (sellPrice * j > 2000000000) {
								pc.sendPackets(new S_ServerMessage(904, // ??????????????????%d????????????????????????????????????
										"2000000000"));
								targetPc.setTradingInPrivateShop(false);
								return;
							}
						}
						price = count * sellPrice;
						if (pc.getInventory().checkItem(L1ItemId.ADENA, price)) {
							L1ItemInstance adena = pc.getInventory()
									.findItemId(L1ItemId.ADENA);
							if ((targetPc != null) && (adena != null)) {
								if (targetPc.getInventory().tradeItem(item,
										count, pc.getInventory()) == null) {
									targetPc.setTradingInPrivateShop(false);
									return;
								}
								pc.getInventory().tradeItem(adena, price,
										targetPc.getInventory());
								String message = item.getItem().getName()
										+ " (" + String.valueOf(count) + ")";
								targetPc.sendPackets(new S_ServerMessage(877, // %1%o
										// %0????????????????????????
										pc.getName(), message));
								pssl.setSellCount(count + sellCount);
								sellList.set(order, pssl);
								if (pssl.getSellCount() == pssl
										.getSellTotalCount()) { // ?????????????????????????????????
									isRemoveFromList[order] = true;
								}
							}
						} else {
							pc.sendPackets(new S_ServerMessage(189)); // \f1????????????????????????????????????
							break;
						}
					} else {
						pc.sendPackets(new S_ServerMessage(270)); // \f1?????????????????????????????????????????????????????????
						break;
					}
				}
				// ????????????????????????????????????????????????????????????
				for (int i = 7; i >= 0; i--) {
					if (isRemoveFromList[i]) {
						sellList.remove(i);
					}
				}
				targetPc.setTradingInPrivateShop(false);
			}
		} else if ((resultType == 1) && (size != 0) && isPrivateShop) { // ?????????????????????????????????
			int count;
			int order;
			List<L1PrivateShopBuyList> buyList;
			L1PrivateShopBuyList psbl;
			int itemObjectId;
			L1ItemInstance item;
			int buyPrice;
			int buyTotalCount;
			int buyCount;
			boolean[] isRemoveFromList = new boolean[8];

			L1PcInstance targetPc = null;
			if (findObject instanceof L1PcInstance) {
				targetPc = (L1PcInstance) findObject;
			}
			if (targetPc.isTradingInPrivateShop()) {
				return;
			}
			targetPc.setTradingInPrivateShop(true);
			buyList = targetPc.getBuyList();

			for (int i = 0; i < size; i++) {
				itemObjectId = readD();
				count = readCH();
				order = readC();
				item = pc.getInventory().getItem(itemObjectId);
				if (item == null) {
					continue;
				}
				psbl = buyList.get(order);
				buyPrice = psbl.getBuyPrice();
				buyTotalCount = psbl.getBuyTotalCount(); // ?????????????????????
				buyCount = psbl.getBuyCount(); // ???????????????
				if (count > buyTotalCount - buyCount) {
					count = buyTotalCount - buyCount;
				}
				if (item.isEquipped()) {
					// pc.sendPackets(new S_ServerMessage(905)); // ?????????????????????????????????
					continue;
				}
	            if (item.getBless() >= 128) { // ??????????????????
	                // pc.sendPackets(new S_ServerMessage(210, item.getItem().getName())); // \f1%0%d?????????????????????
	                continue;
	             }

				if (targetPc.getInventory().checkAddItem(item, count) == L1Inventory.OK) { // ?????????????????????????????????????????????
					for (int j = 0; j < count; j++) { // ????????????????????????????????????
						if (buyPrice * j > 2000000000) {
							targetPc.sendPackets(new S_ServerMessage(904, // ??????????????????%d????????????????????????????????????
									"2000000000"));
							return;
						}
					}
					if (targetPc.getInventory().checkItem(L1ItemId.ADENA,
							count * buyPrice)) {
						L1ItemInstance adena = targetPc.getInventory()
								.findItemId(L1ItemId.ADENA);
						if (adena != null) {
							targetPc.getInventory().tradeItem(adena,
									count * buyPrice, pc.getInventory());
							pc.getInventory().tradeItem(item, count,
									targetPc.getInventory());
							psbl.setBuyCount(count + buyCount);
							buyList.set(order, psbl);
							if (psbl.getBuyCount() == psbl.getBuyTotalCount()) { // ?????????????????????????????????
								isRemoveFromList[order] = true;
							}
						}
					} else {
						targetPc.sendPackets(new S_ServerMessage(189)); // \f1????????????????????????????????????
						break;
					}
				} else {
					pc.sendPackets(new S_ServerMessage(271)); // \f1????????????????????????????????????????????????????????????
					break;
				}
			}
			// ????????????????????????????????????????????????????????????
			for (int i = 7; i >= 0; i--) {
				if (isRemoveFromList[i]) {
					buyList.remove(i);
				}
			}
			targetPc.setTradingInPrivateShop(false);
		} else if ((resultType == 12) && (size != 0)
				&& npcImpl.equalsIgnoreCase("L1Merchant")) { // ????????????
			int petCost, petCount, divisor, itemObjectId, itemCount = 0;
			boolean chackAdena = true;

			for (int i = 0; i < size; i++) {
				petCost = 0;
				petCount = 0;
				divisor = 6;
				itemObjectId = readD();
				itemCount = readD();

				if (itemCount == 0) {
					continue;
				}
				for (L1NpcInstance petNpc : pc.getPetList().values()) 
					petCost += petNpc.getPetcost();

				int charisma = pc.getCha();
				if (pc.isCrown()) { // ??????
					charisma += 6;
				} else if (pc.isElf()) { // ??????
					charisma += 12;
				} else if (pc.isWizard()) { // ??????
					charisma += 6;
				} else if (pc.isDarkelf()) { // ????????????
					charisma += 6;
				} else if (pc.isDragonKnight()) { // ?????????
					charisma += 6;
				} else if (pc.isIllusionist()) { // ?????????
					charisma += 6;
				}

				if (!pc.getInventory().consumeItem(L1ItemId.ADENA, 115)) {
					chackAdena = false;
				}
				L1Pet l1pet = PetTable.getInstance().getTemplate(itemObjectId);
				if (l1pet != null && chackAdena) {
					npcId = l1pet.get_npcid();
					charisma -= petCost;
					if ((npcId == 45313) || (npcId == 45710 // ????????????????????????????????????
							) || (npcId == 45711) || (npcId == 45712) || (npcId == 46046)) { // ??????????????????????????????
						divisor = 12;
					} else {
						divisor = 6;
					}
					petCount = charisma / divisor;
					if (petCount <= 0) {
						pc.sendPackets(new S_ServerMessage(489)); // ???????????????????????????????????????
						return;
					}
					L1Npc npcTemp = NpcTable.getInstance().getTemplate(npcId);
					L1PetInstance pet = new L1PetInstance(npcTemp, pc, l1pet);
					pet.setPetcost(divisor);
				}
			}
			if (!chackAdena) {
				pc.sendPackets(new S_ServerMessage(189)); // \f1???????????????
			}
		}
	}

	@Override
	public String getType() {
		return C_RESULT;
	}

}
