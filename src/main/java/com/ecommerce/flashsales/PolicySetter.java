package com.ecommerce.flashsales;
/***
 * 
 * @author wuwesley
 * the class for policy setter
 */

public class PolicySetter {
	public String goodsSKU;
	public int userLevel;
	public int quantityLimit;
	
	public String getGoodsSKU() {
		return goodsSKU;
	}
	public void setGoodsSKU(String goodsSKU) {
		this.goodsSKU = goodsSKU;
	}
	public int getUserLevel() {
		return userLevel;
	}
	public void setUserLevel(int userLevel) {
		this.userLevel = userLevel;
	}
	public int getQuantityLimit() {
		return quantityLimit;
	}
	public void setQuantityLimit(int quantityLimit) {
		this.quantityLimit = quantityLimit;
	}
	@Override
	public String toString() {
		return "PolicySetter [goodsSKU=" + goodsSKU + ", userLevel=" + userLevel + ", quantityLimit=" + quantityLimit
				+ "]";
	}
	
}
