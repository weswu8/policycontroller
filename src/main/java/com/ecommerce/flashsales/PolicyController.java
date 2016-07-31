package com.ecommerce.flashsales;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import net.rubyeye.xmemcached.MemcachedClient;
import net.rubyeye.xmemcached.exception.MemcachedException;

/***
 * 
 * @author wuwesley
 * The business policy for the whole system.
 */
@RestController
@RequestMapping("/policy")
public class  PolicyController {	
    @Autowired
    private MemcachedClient memcachedClient;
    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final String allItemsKey = "xAllPoliciesKey";
    private final String xNameSpace = "PlicyController";
    /***
     * Generate the md5 value for the pair of GoodsSku and Inventory no.
     * @param badguy
     * @return
     * @throws NoSuchAlgorithmException 
     */
    public String md5Hashing (String xNameSpace,String goodsSku) throws NoSuchAlgorithmException{
		String md5String = null;
		String clientPair = null;
		
		clientPair = goodsSku + ":" + xNameSpace;
		
		MessageDigest md = MessageDigest.getInstance("MD5");
        md.update(clientPair.toString().getBytes());
        
        byte byteData[] = md.digest();
 
        //convert the byte to hex format method 1
        StringBuffer sb = new StringBuffer();
        for (int i = 0; i < byteData.length; i++) {
         sb.append(Integer.toString((byteData[i] & 0xff) + 0x100, 16).substring(1));
        }
        //System.out.println("Digest(in hex format):: " + sb.toString());
        md5String = sb.toString();
		return md5String;
	}
    /***
	 * Create a new the policy record
	 * Request sample : {"goodsSKU":"QT3456","userLevel":5,"quantityLimit":100} and need set the customer header -H 'Content-Type:application/json'
	 * Response sample : {"goodsSKU":"QT3456","userLevel":5,"quantityLimit":100}
     * @throws ParseException 
     * @throws NoSuchAlgorithmException 
	 */
	@SuppressWarnings("unchecked")
	@RequestMapping(method=RequestMethod.POST, value = "/add", headers = "Accept=application/json")
	public PolicySetter addPolicy(@RequestBody PolicySetter policySetter) throws ParseException, NoSuchAlgorithmException {
		long timeMillis = System.currentTimeMillis();
		long timeSeconds = TimeUnit.MILLISECONDS.toSeconds(timeMillis);
		int expirationValue = (int) (timeSeconds + 24*60*60*365);
		JSONObject jObj = new JSONObject();
		if (policySetter.getGoodsSKU().length() > 0 && policySetter.getGoodsSKU() != null){
 			try {
 				jObj.put("goodsSKU",policySetter.getGoodsSKU());
 				jObj.put("userLevel",policySetter.getUserLevel());
 				jObj.put("quantityLimit",policySetter.getQuantityLimit());
 				memcachedClient.set(md5Hashing(xNameSpace, policySetter.getGoodsSKU()), expirationValue, jObj.toString());
 				updateAllItemsKey(policySetter.getGoodsSKU(),"ADD");
			} catch (TimeoutException e) {
				logger.error("TimeoutException");
			} catch (InterruptedException e) {
				logger.error("InterruptedException");
			} catch (MemcachedException e) {
				logger.error("MemcachedException");
			}
 		}
		return policySetter;
	}
	
	/***
	 * Get the policy info by the sku No.
	 * Request sample : http://localhost:8080/policy/sku/{sku}
	 * Response sample : {"goodsSKU":"QT3456","userLevel":5,"quantityLimit":100}
	 * @throws ParseException 
	 * @throws NoSuchAlgorithmException 
	 */
	@RequestMapping(method = RequestMethod.GET, value="/sku/{sku}")	
	public PolicySetter getPolicy(@PathVariable("sku") String sku) throws ParseException, NoSuchAlgorithmException {
		Object mObject = null;
		PolicySetter policySetter = new PolicySetter();
		if (sku.length() > 0 && sku != null){
 			try {
 				mObject = memcachedClient.get(md5Hashing(xNameSpace, sku));
 				if (mObject != null){
 					JSONParser parser = new JSONParser();
 					JSONObject json = (JSONObject) parser.parse(mObject.toString());
 					policySetter.setGoodsSKU(json.get("goodsSKU").toString());
 					policySetter.setQuantityLimit(Integer.parseInt(json.get("quantityLimit").toString()));
 					policySetter.setUserLevel(Integer.parseInt(json.get("userLevel").toString()));
 				}				
			} catch (TimeoutException e) {
				logger.error("TimeoutException");
			} catch (InterruptedException e) {
				logger.error("InterruptedException");
			} catch (MemcachedException e) {
				logger.error("MemcachedException");
			}
 		}
		return policySetter;
	}
	/***
	 * update the policy info
	 * Request URL : http://localhost:8080/policy/update
	 * Request sample : {"goodsSKU":"QT3456","goodsQuantity":100} and need set the customer header -H 'Content-Type:application/json'
	 * Response sample : {"goodsSKU":"QT3456","userLevel":5,"quantityLimit":100}
	 * @throws NoSuchAlgorithmException 
	 */
	@SuppressWarnings("unchecked")
	@RequestMapping(method = RequestMethod.PUT, value = "/update", headers = "Accept=application/json")	
	public PolicySetter updatePolicy(@RequestBody PolicySetter policySetter) throws NoSuchAlgorithmException {
		long timeMillis = System.currentTimeMillis();
		long timeSeconds = TimeUnit.MILLISECONDS.toSeconds(timeMillis);
		int expirationValue = (int) (timeSeconds + 24*60*60*365);
		JSONObject jObj = new JSONObject();
		if (policySetter.getGoodsSKU().length() > 0 && policySetter.getGoodsSKU() != null){
 			try {
 				jObj.put("goodsSKU",policySetter.getGoodsSKU());
 				jObj.put("userLevel",policySetter.getUserLevel());
 				jObj.put("quantityLimit",policySetter.getQuantityLimit());
 				memcachedClient.replace(md5Hashing(xNameSpace, policySetter.getGoodsSKU()), expirationValue, jObj.toString());
			} catch (TimeoutException e) {
				logger.error("TimeoutException");
			} catch (InterruptedException e) {
				logger.error("InterruptedException");
			} catch (MemcachedException e) {
				logger.error("MemcachedException");
			}
 		}
		return policySetter;
	}
	/***
	 * Delete the policy record by sku
	 * Request sample : http://localhost:8080/policy/delete/sku/{sku}
	 * Response sample : {"goodsSKU":null,"userLevel":0,"quantityLimit":0}
	 * @throws ParseException 
	 * @throws NoSuchAlgorithmException 
	 */
	@RequestMapping(method=RequestMethod.DELETE, value = "/delete/sku/{sku}")
	public PolicySetter removePolicy(@PathVariable("sku") String sku) throws ParseException, NoSuchAlgorithmException {
		PolicySetter policySetter = new PolicySetter() ;
		if (sku.length() > 0 && sku != null){
 			try {
				memcachedClient.delete(md5Hashing(xNameSpace, sku));
				updateAllItemsKey(sku,"DELETE");
			} catch (TimeoutException e) {
				logger.error("TimeoutException");
			} catch (InterruptedException e) {
				logger.error("InterruptedException");
			} catch (MemcachedException e) {
				logger.error("MemcachedException");
			}
 		}
		return policySetter;
	}
	/***
	 * find all items
	 * @throws ParseException 
	 * @throws NoSuchAlgorithmException 
	 */
	@RequestMapping(method = RequestMethod.GET,value="/all")
	public List<PolicySetter> findAllItems() throws ParseException, NoSuchAlgorithmException{
		List<PolicySetter> glist = new ArrayList<>();
		List<String> mlist = null;
		Object mObject = null;
		try {
			mObject = memcachedClient.get(allItemsKey);
			if (mObject != null){
				mlist = new ArrayList<String>(Arrays.asList(mObject.toString().split(",")));
				for(String mSku:mlist){
					if (mSku.trim().length() > 0) {
						glist.add(getPolicy(mSku));
					}
				}
			}else{
				glist.add(new PolicySetter());
			}
		} catch (TimeoutException e) {
			logger.error("TimeoutException");
		} catch (InterruptedException e) {
			logger.error("InterruptedException");
		} catch (MemcachedException e) {
			logger.error("MemcachedException");
		}
		return glist;
		
	}
	/***
	 * store the key index for the whole inventory system
	 * allItemsKey(xAllItemsKey):xxx,xxxx,xxxx
	 * @throws ParseException 
	 */
	public void updateAllItemsKey(String theItemKey,String mode) throws ParseException {
		Object mObject = null;
		List<String> mlist = null;
		String tmpItemsKey = null;
		long timeMillis = System.currentTimeMillis();
		long timeSeconds = TimeUnit.MILLISECONDS.toSeconds(timeMillis);
		int expirationValue = (int) (timeSeconds + 24*60*60*365);
		try {
			mObject = memcachedClient.get(allItemsKey);
			if (mObject != null){
				mlist = new ArrayList<String>(Arrays.asList(mObject.toString().split(",")));
				if (mode == "ADD"){
					//avoid the duplicated key issue
					if (mlist.contains(theItemKey) == false){mlist.add(theItemKey);}
				}else{
					mlist.remove(theItemKey);
				}
				tmpItemsKey = mlist.toString().replace("[", "").replace("]", "").replace(" ", "").trim() + ",";
				memcachedClient.replace(allItemsKey, expirationValue, tmpItemsKey);
			}else{
				tmpItemsKey = theItemKey + ",";
				memcachedClient.add(allItemsKey, expirationValue, tmpItemsKey);
			}
		} catch (TimeoutException e) {
			logger.error("TimeoutException");
		} catch (InterruptedException e) {
			logger.error("InterruptedException");
		} catch (MemcachedException e) {
			logger.error("MemcachedException");
		}
	}
	
}
