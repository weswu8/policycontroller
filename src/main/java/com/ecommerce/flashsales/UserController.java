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
 * The user order information service for the whole system.
 */
@RestController
@RequestMapping("/user")
public class  UserController {	
    @Autowired
    private MemcachedClient memcachedClient;
    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final String allItemsKey = "xAllUserOrdersKey";
    private final String xNameSpace = "UserController";
    /***
     * Generate the md5 value for the pair of userID and GoodsSku
     * @param badguy
     * @return
     * @throws NoSuchAlgorithmException 
     */
    public String md5Hashing (UserOrders userOrders) throws NoSuchAlgorithmException{
		String md5String = null;
		String clientPair = null;
		
		clientPair = userOrders.getUserID() + ":" + userOrders.getGoodsSKU() + ":" + xNameSpace;
		
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
	 * Create a new the user order record
	 * Request sample : {"userID":"FS000001","goodsSKU":"QT3456","userLevel":5,"orderQuantity":2} and need set the customer header -H 'Content-Type:application/json'
	 * Response sample : {"userID":"FS000001","goodsSKU":"QT3456","userLevel":5,"orderQuantity":2}
     * @throws NoSuchAlgorithmException 
     * @throws ParseException 
	 */
	@SuppressWarnings("unchecked")
	@RequestMapping(method=RequestMethod.POST, value = "/add", headers = "Accept=application/json")
	public UserOrders addUserOrders(@RequestBody UserOrders userOrders) throws NoSuchAlgorithmException, ParseException {
		long timeMillis = System.currentTimeMillis();
		long timeSeconds = TimeUnit.MILLISECONDS.toSeconds(timeMillis);
		int expirationValue = (int) (timeSeconds + 24*60*60*365);
		JSONObject jObj = new JSONObject();
		if (userOrders.getUserID().length() > 0 && userOrders.getUserID() != null){
 			try {
 				jObj.put("userID",userOrders.getUserID());
 				jObj.put("goodsSKU",userOrders.getGoodsSKU());
 				jObj.put("userLevel",userOrders.getUserLevel());
 				jObj.put("orderQuantity",userOrders.getOrderQuantity());
 				memcachedClient.set(md5Hashing(userOrders), expirationValue, jObj.toString());
 				// userID@goodsSKU
 				updateAllItemsKey(userOrders.getUserID() + "@" + userOrders.getGoodsSKU(),"ADD");
			} catch (TimeoutException e) {
				logger.error("TimeoutException");
			} catch (InterruptedException e) {
				logger.error("InterruptedException");
			} catch (MemcachedException e) {
				logger.error("MemcachedException");
			}
 		}
		return userOrders;
	}
	
	/***
	 * Get the user's order info
	 * Request sample : http://localhost:8080/user/userid/{userid}/sku/{sku}
	 * Response sample : {"userID":"FS000001","goodsSKU":"QT3456","userLevel":5,"orderQuantity":2}
	 * @throws ParseException 
	 * @throws NoSuchAlgorithmException 
	 */
	@RequestMapping(method = RequestMethod.GET, value="/userid/{userid}/sku/{sku}")	
	public UserOrders getUserOrders(@PathVariable("userid") String userid, @PathVariable("sku") String sku) throws ParseException, NoSuchAlgorithmException {
		Object mObject = null;
		UserOrders userOrders = new UserOrders();
		JSONParser parser = new JSONParser();
		if (userid.length() > 0 && sku.length() > 0 ){
 			try {
 				userOrders.setUserID(userid);
 				userOrders.setGoodsSKU(sku);
 				mObject = memcachedClient.get(md5Hashing(userOrders));
 				if (mObject == null){
 					userOrders.setOrderQuantity(0);
 				}else{
					JSONObject json = (JSONObject) parser.parse(mObject.toString());
					userOrders.setUserLevel(Integer.parseInt(json.get("userLevel").toString()));
					userOrders.setOrderQuantity(Integer.parseInt(json.get("orderQuantity").toString()));
 				}				
			} catch (TimeoutException e) {
				logger.error("TimeoutException");
			} catch (InterruptedException e) {
				logger.error("InterruptedException");
			} catch (MemcachedException e) {
				logger.error("MemcachedException");
			}
 		}
		return userOrders;
	}
	/***
	 * update the user's order info, the client should read the original value first
	 * Request URL : http://localhost:8080/user/update
	 * Request sample : {"userID":"FS000001","goodsSKU":"QT3456","userLevel":5,"orderQuantity":2} and need set the customer header -H 'Content-Type:application/json'
	 * Response sample : {"userID":"FS000001","goodsSKU":"QT3456","userLevel":5,"orderQuantity":2}
	 * @throws NoSuchAlgorithmException 
	 */
	@SuppressWarnings("unchecked")
	@RequestMapping(method = RequestMethod.PUT, value = "/update", headers = "Accept=application/json")	
	public UserOrders updateUserOrders(@RequestBody UserOrders userOrders) throws NoSuchAlgorithmException {
		long timeMillis = System.currentTimeMillis();
		long timeSeconds = TimeUnit.MILLISECONDS.toSeconds(timeMillis);
		int expirationValue = (int) (timeSeconds + 24*60*60*365);
		JSONObject jObj = new JSONObject();
		if (userOrders.getUserID().length() > 0 && userOrders.getGoodsSKU().length() > 0){
 			try {
 				jObj.put("userID",userOrders.getUserID());
 				jObj.put("goodsSKU",userOrders.getGoodsSKU());
 				jObj.put("userLevel",userOrders.getUserLevel());
 				jObj.put("orderQuantity",userOrders.getOrderQuantity());
 				memcachedClient.replace(md5Hashing(userOrders), expirationValue, jObj.toString());
			} catch (TimeoutException e) {
				logger.error("TimeoutException");
			} catch (InterruptedException e) {
				logger.error("InterruptedException");
			} catch (MemcachedException e) {
				logger.error("MemcachedException");
			}
 		}
		return userOrders;
	}
	/***
	 * Delete the user orders record.
	 * Request sample : http://localhost:8080/user/delete/userid/{userid}/sku/{sku}
	 * Response sample : {"userID":"FS000001","goodsSKU":"QT3456","userLevel":5,"orderQuantity":2}
	 */
	@RequestMapping(method=RequestMethod.DELETE, value = "/delete/userid/{userid}/sku/{sku}")
	public UserOrders removeUserOrders(@PathVariable("userid") String userid, @PathVariable("sku") String sku) throws ParseException, NoSuchAlgorithmException {
		UserOrders userOrders = new UserOrders() ;
		if (userid.length() > 0 && sku.length() > 0){
 			try {
 				userOrders.setUserID(userid);
 				userOrders.setGoodsSKU(sku);
 				memcachedClient.delete(md5Hashing(userOrders));
 				updateAllItemsKey(userOrders.getUserID() + "@" + userOrders.getGoodsSKU(),"DELETE");
			} catch (TimeoutException e) {
				logger.error("TimeoutException");
			} catch (InterruptedException e) {
				logger.error("InterruptedException");
			} catch (MemcachedException e) {
				logger.error("MemcachedException");
			}
 		}
		return userOrders;
	}
	/***
	 * find all items
	 * @throws ParseException 
	 * @throws NoSuchAlgorithmException 
	 */
	@RequestMapping(method = RequestMethod.GET,value="/all")
	public List<UserOrders> findAllItems() throws ParseException, NoSuchAlgorithmException{
		List<UserOrders> glist = new ArrayList<>();
		List<String> mlist = null, xlist =null;
		Object mObject = null;
		try {
			mObject = memcachedClient.get(allItemsKey);
			if (mObject != null){
				mlist = new ArrayList<String>(Arrays.asList(mObject.toString().split(",")));
				for(String mSku:mlist){
					if (mSku.trim().length() > 0) {
						// userID@goodsSKU
						xlist = new ArrayList<String>(Arrays.asList(mSku.toString().split("@")));			
						glist.add(getUserOrders(xlist.get(0),xlist.get(1)));
					}
				}
			}else{
				glist.add(new UserOrders());
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
