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
    private final Logger logger = LoggerFactory.getLogger(this.getClass().getName());
	FlashSalesAccessLogger fsAccessLogger = new FlashSalesAccessLogger();
	/*** rate limiter setting ***/
    @Value("${ratelimiter.consumeCount}")
	public double consumeCount;
    
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
		
		/*** update the user order quantity ***/
		policyValidationR.setOrderQuantity(userOrders.getOrderQuantity() + quantity);
				
		/*** get the policy setting for the sku ***/
		policySetter = policyController.getPolicy(policyValidationR.getGoodsSKU());
		
		/*** validate the user level and quantity limitation ***/
		if (policySetter.getGoodsSKU() != null){
			if (policyValidationR.getUserLevel() < policySetter.getUserLevel() || policyValidationR.getOrderQuantity() > policySetter.getQuantityLimit()) {
					policyValidationR.setIsAllowed(false);
			/*** this user pass,fill the quantity limitation, this parameter will be used in shopping cart service ***/
			}else{
				policyValidationR.setQuantityLimit(policySetter.getQuantityLimit());
			}
		}
		
		/*** log the info ***/
		long endTime = System.currentTimeMillis();
		fsAccessLogger.doAccessLog(httpRequest, httpResponse, policyValidationR.getSessionID(), CurrentStep.POLICYCONTROLLER.msgBody(), paramsJSON.toString(), endTime-startTime, policyValidationR);
		
		return policyValidationR;
	}
}
