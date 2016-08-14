package com.ecommerce.flashsales;

import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.json.simple.JSONObject;
import org.json.simple.parser.ParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.core.JsonProcessingException;

/***
 * 
 * @author wuwesley
 * The user order information service for the whole system.
 */
@RestController
@RequestMapping("/")
public class  ValidatorController {	
	/*** indicate current version of this micro service ***/
	public final String cVersion = "1.0";
	
	@Autowired
	PolicyController policyController;
	@Autowired
	UserController userController;
    private final Logger logger = LoggerFactory.getLogger("SystemLog");
	FlashSalesAccessLogger fsAccessLogger = new FlashSalesAccessLogger();
	@Value("${shoppingcart.url}")
	private String shoppingcartBaseUrl;
	/*** rate limiter setting ***/
    @Value("${ratelimiter.consumeCount}")
	public double consumeCount;
    
    /***
     * customize the HTTP connection configuration.
     * @return
     */
    @Autowired
    public ClientHttpRequestFactory getClientHttpRequestFactory() {
        /*** set the long time out period ***/
    	int timeout = 60000;
        HttpComponentsClientHttpRequestFactory clientHttpRequestFactory = new HttpComponentsClientHttpRequestFactory();
        clientHttpRequestFactory.setConnectTimeout(timeout);
        return clientHttpRequestFactory;
    }
    /***
     * get the user's goods from shopping cart.
     * @param restTemplate
     * @param userid
     * @param sku
     * @return {false,true},0-index: Exception, 1-index:validation result, 2-index:is throttled
     */
    public AddGoodsR getGoodsFromCart(String userid, String sku){
    	RestTemplate restTemplate = new RestTemplate(getClientHttpRequestFactory());
        restTemplate.setErrorHandler(new ClientErrorHandler());
    	AddGoodsR addGoodsR = null;
		try {
			Map<String, String> params = new HashMap<String, String>();
			params.put("userid", userid);
			params.put("sku", sku);
			addGoodsR = restTemplate.getForObject(shoppingcartBaseUrl+"/userid/{userid}/sku/{sku}",AddGoodsR.class,params);
		} catch (ResourceNotFoundException nEx) {
        	logger.error(nEx.toString());
		} catch (UnexpectedHttpException uEx){
			logger.error(uEx.toString());
		} catch (ResourceAccessException rEx){
			logger.error(rEx.toString());
		}
		return addGoodsR;
    }
    
    /***
	 * do the validation
	 * Request sample : http://localhost:8080/validate/sid/{xxx}/userid/{xxx}/userlv/{userlv}/sku/{xxx}/quantity/{xxx}
	 * Response sample : {"userID":"FS000001","goodsSKU":"QT3456","userLevel":5,"orderQuantity":2, "isAllowed":true}
	 * @throws ParseException 
	 * @throws NoSuchAlgorithmException 
     * @throws JsonProcessingException 
	 */
	@SuppressWarnings("unchecked")
	@RequestMapping(method = RequestMethod.GET ,value = "/validate/sid/{sid}/userid/{userid}/userlv/{userlv}/sku/{sku}/quantity/{quantity}")	
	public PolicyValidationR doPolicyValidation(HttpServletRequest httpRequest, HttpServletResponse httpResponse, @PathVariable("sid") String sid, @PathVariable("userid") String userid, @PathVariable("userlv") int userlv, @PathVariable("sku") String sku, @PathVariable("quantity") int quantity) throws JsonProcessingException, NoSuchAlgorithmException, ParseException {
		PolicyValidationR policyValidationR = new PolicyValidationR();		
		UserOrders userOrders = new UserOrders();
		PolicySetter policySetter = new PolicySetter();
		AddGoodsR addGoodsR = new AddGoodsR();
		long startTime = System.currentTimeMillis();
		
		/*** generate request parameters for log */
		JSONObject paramsJSON = new JSONObject();
		paramsJSON.put("sid", sid);
		paramsJSON.put("sku", sku);
		paramsJSON.put("userid", userid);
		paramsJSON.put("quantity", quantity);
		
		/*** initialize the result ****/
		policyValidationR.setSessionID(sid);
		policyValidationR.setUserID(userid);
		policyValidationR.setUserLevel(userlv);
		policyValidationR.setGoodsSKU(sku);
		policyValidationR.setVersion(cVersion);
		
		/*** default is true, that is default is no limitation ***/
		policyValidationR.setIsAllowed(true);
		
		/*** rate limiter checking ***/
		if (PolicyControllerApplication.rateLimiter.consume(consumeCount) == false){
			policyValidationR.setIsAllowed(false);
			policyValidationR.setIsThrottled(true);
			long endTime = System.currentTimeMillis();
			fsAccessLogger.doAccessLog(httpRequest, httpResponse, policyValidationR.getSessionID(), CurrentStep.POLICYCONTROLLER.msgBody(), paramsJSON.toString(), endTime-startTime, policyValidationR);
			return policyValidationR;
		}
		
		/*** get order history for the user and sku ***/
		userOrders = userController.getUserOrders(policyValidationR.getUserID(), policyValidationR.getGoodsSKU());
		
		/*** get the goods quantity from the user's shopping cart ***/
		addGoodsR = getGoodsFromCart(userid, sku);
		if (addGoodsR.getIsThrottled() == true){
			policyValidationR.setIsAllowed(false);
			policyValidationR.setIsThrottled(true);
			long endTime = System.currentTimeMillis();
			fsAccessLogger.doAccessLog(httpRequest, httpResponse, policyValidationR.getSessionID(), CurrentStep.SHOPPINGCART.msgBody(), paramsJSON.toString(), endTime-startTime, policyValidationR);
			return policyValidationR;
		}
		/*** update the user order quantity ***/
		policyValidationR.setOrderQuantity(userOrders.getOrderQuantity() + addGoodsR.goodsQuantity + quantity);
				
		/*** get the policy setting for the sku ***/
		policySetter = policyController.getPolicy(policyValidationR.getGoodsSKU());
		
		/*** validate the user level and quantity limitation ***/
		if (policySetter.getGoodsSKU() != null){
			if (policyValidationR.getUserLevel() < policySetter.getUserLevel() || policyValidationR.getOrderQuantity() > policySetter.getQuantityLimit()) {
					policyValidationR.setIsAllowed(false);
			}
		}
		
		/*** log the info ***/
		long endTime = System.currentTimeMillis();
		fsAccessLogger.doAccessLog(httpRequest, httpResponse, policyValidationR.getSessionID(), CurrentStep.POLICYCONTROLLER.msgBody(), paramsJSON.toString(), endTime-startTime, policyValidationR);
		
		return policyValidationR;
	}
}
