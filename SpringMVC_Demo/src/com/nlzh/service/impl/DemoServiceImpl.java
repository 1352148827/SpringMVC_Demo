package com.nlzh.service.impl;

import com.nlzh.annotation.Service;
import com.nlzh.service.DemoService;

@Service
public class DemoServiceImpl implements DemoService {

	@Override
	public void fun1() {
		System.err.println("service的fun1方法");
	}

}
