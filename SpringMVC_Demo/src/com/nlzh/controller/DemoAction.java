package com.nlzh.controller;

import com.nlzh.annotation.Autowried;
import com.nlzh.annotation.Controller;
import com.nlzh.annotation.RequestMapping;
import com.nlzh.service.DemoService;

@Controller
@RequestMapping("/demo")
public class DemoAction {

	@Autowried
	private DemoService demoService;
	
	@RequestMapping("/fun1")
	public void fun1(){
		System.err.println("=============fun1===================");
		demoService.fun1();
	}
}
