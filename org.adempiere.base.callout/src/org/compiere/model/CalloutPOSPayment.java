/******************************************************************************
 * Product: Adempiere ERP & CRM Smart Business Solution                       *
 * Copyright (C) 1999-2006 ComPiere, Inc. All Rights Reserved.                *
 * This program is free software; you can redistribute it and/or modify it    *
 * under the terms version 2 of the GNU General Public License as published   *
 * by the Free Software Foundation. This program is distributed in the hope   *
 * that it will be useful, but WITHOUT ANY WARRANTY; without even the implied *
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.           *
 * See the GNU General Public License for more details.                       *
 * You should have received a copy of the GNU General Public License along    *
 * with this program; if not, write to the Free Software Foundation, Inc.,    *
 * 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA.                     *
 * For the text or an alternative of this public license, you may reach us    *
 * ComPiere, Inc., 2620 Augustine Dr. #245, Santa Clara, CA 95054, USA        *
 * or via info@compiere.org or http://www.compiere.org/license.html           *
 *****************************************************************************/
package org.compiere.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.util.List;
import java.util.Properties;
import java.util.logging.Level;

import org.compiere.util.Env;

/**
 * POS Payment Callouts. org.compiere.model.CalloutPOSPayment.*
 * @author David Marquez
 * @version CalloutPOSPayment.java,v 1.3 2022/01/18 16:21:03
 */
public class CalloutPOSPayment extends CalloutEngine
{	
	/**
	 * Payment_Amounts. Change of: 
	 * - IsOverUnderPayment -> set OverUnderAmt to 0
	 * - PayAmt, DiscountAmt, WriteOffAmt, OverUnderAmt -> PayAmt make sure that add up to InvoiceOpenAmt
	 * @param ctx context
	 * @param WindowNo current Window No
	 * @param mTab Grid Tab
	 * @param mField Grid Field
	 * @param value New Value
	 * @param oldValue Old Value
	 * @return null or error message
	 */
	public String amounts(Properties ctx, int WindowNo, GridTab mTab,
		GridField mField, Object value, Object oldValue)
	{		
		// assuming it is resetting value
		if (isCalloutActive ()) 
			return "";
		
		// Order
		int C_Order_ID = Env.getContextAsInt (ctx, WindowNo, "C_Order_ID");
		MOrder ord = new MOrder (ctx, C_Order_ID, null);
		
		// New Payment
		int C_POSPayment_ID = Env.getContextAsInt (ctx, WindowNo, "C_POSPayment_ID");
		if (C_POSPayment_ID == 0 && C_Order_ID == 0) 
			return "";
		
		// PrePayment
		if ("Y".equals (Env.getContext (ctx, WindowNo, "IsPrepayment")))
			return "";
		
		// Changed Column
		String colName = mField.getColumnName ();
		if (colName.equals ("IsOverUnderPayment") // Set Over/Under Amt to
			// Zero
			|| !"Y".equals (Env
				.getContext (ctx, WindowNo, "IsOverUnderPayment")))
			mTab.setValue ("OverUnderAmt", Env.ZERO);
		
		System.out.println("Test On Column " + colName);
		
		// Get Flag, Amount & Order Currency
		BigDecimal OrderAmt = Env.ZERO;
		int C_Currency_Order_ID = 0;
		boolean processed = false;
		if (C_Order_ID != 0)
		{
			processed = ord.isProcessed();
			C_Currency_Order_ID = ord.getC_Currency_ID();
			OrderAmt = ord.getGrandTotal();
			if (OrderAmt == null)
				OrderAmt = Env.ZERO;
					
		} // get Order Info
		if (log.isLoggable(Level.FINE)) log.fine ("Amt=" + OrderAmt + ", C_Order_ID=" + C_Order_ID
			+ ", C_Currency_ID=" + C_Currency_Order_ID);
		
		// Get Info from Tab
		BigDecimal PayAmt = (BigDecimal)mTab.getValue ("PayAmt");
		if (PayAmt == null)
			PayAmt = Env.ZERO;
		BigDecimal DiscountAmt = (BigDecimal)mTab.getValue ("DiscountAmt");
		if (DiscountAmt == null)
			DiscountAmt = Env.ZERO;
		BigDecimal WriteOffAmt = (BigDecimal)mTab.getValue ("WriteOffAmt");
		if (WriteOffAmt == null)
			WriteOffAmt = Env.ZERO;
		BigDecimal OverUnderAmt = (BigDecimal)mTab.getValue ("OverUnderAmt");
		if (OverUnderAmt == null)
			OverUnderAmt = Env.ZERO;
		if (log.isLoggable(Level.FINE)) log.fine ("Pay=" + PayAmt + ", Discount=" + DiscountAmt + ", WriteOff="
			+ WriteOffAmt + ", OverUnderAmt=" + OverUnderAmt);
		
		// Get Currency Info
		Integer C_Currency_ID = (Integer) mTab.getValue ("C_Currency_ID");
		C_Currency_ID = (C_Currency_ID == null) ? Integer.valueOf(0) : C_Currency_ID.intValue();
		MCurrency Currency = MCurrency.get (ctx, C_Currency_ID);
		Timestamp ConvDate = (Timestamp)mTab.getValue ("DateTrx");
		Integer C_ConversionType_ID = (Integer)mTab.getValue ("C_ConversionType_ID");
		C_ConversionType_ID = (C_ConversionType_ID != null) ? C_ConversionType_ID.intValue () : Integer.valueOf(0);
		int AD_Client_ID = Env.getContextAsInt (ctx, WindowNo, "AD_Client_ID");
		int AD_Org_ID = Env.getContextAsInt (ctx, WindowNo, "AD_Org_ID");
		
		// Get Currency Rate
		BigDecimal CurrencyRate = Env.ONE;
		if ((C_Currency_ID > 0 && C_Currency_Order_ID > 0 && C_Currency_ID != C_Currency_Order_ID)
			|| colName.equals ("C_Currency_ID")
			|| colName.equals ("C_ConversionType_ID"))
		{
			if (log.isLoggable(Level.FINE)) 
				log.fine ("OrdCurrency=" + C_Currency_Order_ID + ", PayCurrency=" + C_Currency_ID + ", Date=" + ConvDate + ", Type=" + C_ConversionType_ID);
			
			CurrencyRate = MConversionRate.getRate (C_Currency_Order_ID, C_Currency_ID, ConvDate, C_ConversionType_ID, AD_Client_ID, AD_Org_ID);
			if (CurrencyRate == null || CurrencyRate.compareTo (Env.ZERO) == 0)
			{
				if (C_Currency_Order_ID == 0)
					return "";
				return "NoCurrencyConversion";
			}
			OrderAmt = OrderAmt.multiply (CurrencyRate).setScale (Currency.getStdPrecision (), RoundingMode.HALF_UP);
			
			if (log.isLoggable(Level.FINE)) 
				log.fine ("Rate=" + CurrencyRate + ", OrderAmt=" + OrderAmt);
		}
		System.out.println("Order Amt " + OrderAmt);
		
		// Get all POSPayment
		List<X_C_POSPayment> pps = new Query(ctx, X_C_POSPayment.Table_Name, "C_Order_ID=?", ord.get_TrxName())
			.setParameters(C_Order_ID)
			.setOnlyActiveRecords(true)
			.list();
		BigDecimal totalPOSPayments = Env.ZERO; 
		BigDecimal payamt = Env.ZERO;
		for (X_C_POSPayment pp : pps) {
			 payamt = pp.getPayAmt();
			 if(!pp.IsPrepayment()) // Avoid unnecessary amounts
				 payamt.add(pp.getDiscountAmt()).add(pp.getOverUnderAmt()).add(pp.getWriteOffAmt());
			 
			 payamt = MConversionRate.convert(ctx, payamt, 
					pp.getC_Currency_ID(), ord.getC_Currency_ID(), ord.getDateAcct(), 
					pp.getC_ConversionType_ID(), pp.getAD_Client_ID(), pp.getAD_Org_ID());
			 
			 if (pp.getC_POSPayment_ID() != C_POSPayment_ID)
				 totalPOSPayments = totalPOSPayments.add(payamt);
		}	
		
		if (totalPOSPayments != Env.ZERO) {
			totalPOSPayments = totalPOSPayments.multiply(CurrencyRate).setScale(Currency.getStdPrecision(), RoundingMode.HALF_UP);
			System.out.println("POSPayments: " + totalPOSPayments);
			OrderAmt = OrderAmt.subtract(totalPOSPayments);
			System.out.println("Due Amt: " + OrderAmt);
		}
			
		
		// Currency Changed - convert all
		if (colName.equals ("C_Currency_ID")
			|| colName.equals ("C_ConversionType_ID"))
		{
			PayAmt = PayAmt.multiply (CurrencyRate).setScale (Currency.getStdPrecision (), RoundingMode.HALF_UP);
			mTab.setValue ("PayAmt", PayAmt);
			DiscountAmt = DiscountAmt.multiply (CurrencyRate).setScale (Currency.getStdPrecision (), RoundingMode.HALF_UP);
			mTab.setValue ("DiscountAmt", DiscountAmt);
			WriteOffAmt = WriteOffAmt.multiply (CurrencyRate).setScale (Currency.getStdPrecision (), RoundingMode.HALF_UP);
			mTab.setValue ("WriteOffAmt", WriteOffAmt);
			OverUnderAmt = OverUnderAmt.multiply (CurrencyRate).setScale (Currency.getStdPrecision (), RoundingMode.HALF_UP);
			mTab.setValue ("OverUnderAmt", OverUnderAmt);
		}
		// No Order - Set Discount, Writeoff, Under/Over to 0
		else if (C_Order_ID == 0)
		{
			if (Env.ZERO.compareTo (DiscountAmt) != 0)
				mTab.setValue ("DiscountAmt", Env.ZERO);
			if (Env.ZERO.compareTo (WriteOffAmt) != 0)
				mTab.setValue ("WriteOffAmt", Env.ZERO);
			if (Env.ZERO.compareTo (OverUnderAmt) != 0)
				mTab.setValue ("OverUnderAmt", Env.ZERO);
		} else {
			
			if (colName.equals ("PayAmt")
				&& (!processed)
				&& "Y".equals (Env.getContext (ctx, WindowNo, "IsOverUnderPayment")))
			{
				System.out.println("1.1");
				OverUnderAmt = OrderAmt.subtract (PayAmt).subtract(DiscountAmt).subtract (WriteOffAmt);
				if (OverUnderAmt.signum() > 0) { // no discount because is not paid in full
					System.out.println("1.1.1");
					DiscountAmt = Env.ZERO;
					mTab.setValue ("DiscountAmt", DiscountAmt);
					OverUnderAmt = OrderAmt.subtract (PayAmt).subtract(DiscountAmt).subtract (WriteOffAmt);
				}
				mTab.setValue ("OverUnderAmt", OverUnderAmt);
			}
			else if (colName.equals ("PayAmt")
				&& (!processed))
			{
				System.out.println("1.2");
				WriteOffAmt = OrderAmt.subtract (PayAmt).subtract (
					DiscountAmt).subtract (OverUnderAmt);
				mTab.setValue ("WriteOffAmt", WriteOffAmt);
			}
			else if (colName.equals ("IsOverUnderPayment")
				&& (!processed))
			{
				System.out.println("1.3");
				boolean overUnderPaymentActive = "Y".equals (Env.getContext (ctx,
					WindowNo, "IsOverUnderPayment"));
				if (overUnderPaymentActive)
				{
					System.out.println("1.3.1");
					OverUnderAmt = OrderAmt.subtract (PayAmt).subtract (
						DiscountAmt);
					mTab.setValue ("WriteOffAmt", Env.ZERO);
					mTab.setValue ("OverUnderAmt", OverUnderAmt);
				}else{
					System.out.println("1.3.2");
					WriteOffAmt = OrderAmt.subtract (PayAmt).subtract (
						DiscountAmt);
					mTab.setValue ("WriteOffAmt", WriteOffAmt);
					mTab.setValue ("OverUnderAmt", Env.ZERO);
				}
			}
			else if (!processed)
			{
				System.out.println("1.4");		
				
				PayAmt = OrderAmt.subtract (DiscountAmt).subtract (
					WriteOffAmt).subtract (OverUnderAmt);
				mTab.setValue ("PayAmt", PayAmt);
			}
			
			System.out.println("Payment Amt " + PayAmt);
		}
		
		return "";
	} // amounts
} // CalloutPayment
