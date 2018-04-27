package com.nlzh.servlet;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.nlzh.annotation.Autowried;
import com.nlzh.annotation.Controller;
import com.nlzh.annotation.RequestMapping;
import com.nlzh.annotation.Service;




public class DispatcherServlet extends HttpServlet {
	
	private Properties p = new Properties();
	
	/**
	 * 扫描的所有class的全限定名
	 */
	private List<String> classNames = new ArrayList<>();
	
	// 参数
	private final String  contextConfigLocation= "contextConfigLocation";
	
	/**
	 * ioc容器
	 * 		key: beanName
	 * 		value: bean的实例化对象
	 */
	private Map<String, Object> ioc = new HashMap<>();
	
	/**
	 * 支持器
	 */
	private Map<String, Handler> handlerMapping = new HashMap<String, Handler>();

	@Override
	public void init() throws ServletException {
		ServletConfig config = this.getServletConfig();
		
		// 1 加载配置文件
		doLoadConfig(config.getInitParameter(contextConfigLocation));
		
		// 2 根据配置文件扫描所有的相关的类
		doScanner(p.getProperty("package"));
		
		// 3 初始化所有的相关类的实例，并将其放入到IOC容器中，也就是map中
		doInstance();
		
		// 4 实现自动依赖注入
		doAutowried();
		// 5 初始化HandlerMapping
		initHandlerMapping();
	}
	
	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		this.doPost(req, resp);
	}

	@Override
	protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		String url = req.getRequestURI();
		String contextPath = req.getContextPath();
		
		url = url.replace(contextPath,"").replace("/+", "/");
		
		if(!handlerMapping.containsKey(url)){
			 resp.sendError(HttpServletResponse.SC_NOT_FOUND, "找不到相关资源");
		}
		
		Handler handler = handlerMapping.get(url);
		
		Method method = handler.getMethod();
		
		try {
			method.invoke(handler.getController());
		} catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
			e.printStackTrace();
		}
		
	}
	

	


	/**
	 * 5 初始化HandlerMapping
	 */
	private void initHandlerMapping() {
		
		// 判断ioc容器是否为null
		if(ioc.isEmpty()) return ;
		
		for (Entry<String,Object> entry : ioc.entrySet()) {
			Class<?> clazz = entry.getValue().getClass();
			
			if(!clazz.isAnnotationPresent(Controller.class)){
				continue;
			}
			
			
			String baseUrl = "";
			if(clazz.isAnnotationPresent(RequestMapping.class)){
				RequestMapping requestMapping = clazz.getAnnotation(RequestMapping.class);
				baseUrl = requestMapping.value();
			}
			
			Method[] methods = clazz.getMethods();
			
			try {
				for (Method method : methods) {
					if(method.isAnnotationPresent(RequestMapping.class)){
						RequestMapping requestMapping = method.getAnnotation(RequestMapping.class);
						String url = (baseUrl+requestMapping.value()).replaceAll("/+", "/")+".do";
						
						Handler handler = new Handler();
						handler.setController(entry.getValue());
						handler.setMethod(method);
						handlerMapping.put(url, handler);
						
					}
				}
				
			} catch (Exception e) {
				e.printStackTrace();
			}
			
		}
	}






	/**
	 * 4 实现自动依赖注入
	 */
	private void doAutowried() {
		
		if(ioc.isEmpty()) return ;
		
		for (Entry<String, Object> entry : ioc.entrySet()) {
			// 获取到所有的字段 Field
			Field[] fields = entry.getValue().getClass().getDeclaredFields();
			for (Field field : fields) {
				if(!field.isAnnotationPresent(Autowried.class)){
					continue;
				}
				
				Autowried autowried = field.getAnnotation(Autowried.class);
				
				String beanName = autowried.value().trim();
				
				// 如果value的值不为null，表示通过value的名称注入对象
				if("".equals(beanName)){
					// 如果控制字符串等于beanName，表示它是默认的首字母小写的bean类
					beanName = field.getType().getName();
				}
				
				// 要想访问私有的，需要暴力访问
				field.setAccessible(true);
				
				try {
					field.set(entry.getValue(), ioc.get(beanName));
					
				} catch (IllegalArgumentException | IllegalAccessException e) {
					e.printStackTrace();
					continue;
				}
				
			}
			
		}
	}






	/**
	 *  3 初始化所有的相关类的实例，并将其放入到IOC容器中，也就是map中
	 */
	private void doInstance() {
		if(classNames.isEmpty()) return;
		
		// 如果不为null，利用反射机制，将所有刚刚扫描进来的classname进行初始化 
		try {
			for (String className : classNames) {
				Class<?> clazz = Class.forName(className);
				
				// 进行bean的实例化阶段,初始化IOC容器
				
				// IOC容器规则
				// 1、 key默认用类名首字母小写
				// 2、 如果用户自定义名字，那么要优先选择自定义名字
				// 3、 如果是接口，我们可以用接口的类型作为key
				
				if(clazz.isAnnotationPresent(Controller.class)){
					// 首字母小写
					String beanName = lowerFirstCase(clazz.getSimpleName());
					
					// 初始化ioc容器，key为beanName，value为对象的实例化
					ioc.put(beanName, clazz.newInstance());
					
				}else if (clazz.isAnnotationPresent(Service.class)) {
					// 2、 如果用户自定义名字，那么要优先选择自定义名字
					Service service = clazz.getAnnotation(Service.class);
					String beanName = service.value();
					
					if("".equals(beanName.trim())){
						beanName = lowerFirstCase(clazz.getSimpleName());
					}
					
					Object instance = clazz.newInstance();
					ioc.put(beanName, instance);
					
					
					// 3、 如果是接口，我们可以用接口的类型作为key
					Class<?>[] intefaces = clazz.getInterfaces();
					for (Class<?> i : intefaces) {
						// 将接口的类型作为key
						ioc.put(i.getName(),instance);
					}
				}else {
					continue;
				}
				
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		
	}






	
	/**
	 * 加载配置文件
	 * @param location
	 */
	private void doLoadConfig(String location) {
		InputStream is = this.getClass().getClassLoader().getResourceAsStream(location);
		
		try {
			p.load(is);
		} catch (IOException e) {
			e.printStackTrace();
		} finally{
			if( null != is){
				try {
					is.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
	}
	
	/**
	 * 2 根据配置文件扫描所有的相关的类
	 * @param property
	 */
	private void doScanner(String packageName) {
		
		// 进行递归扫描
		URL url = this.getClass().getClassLoader().getResource("/"+packageName.replaceAll("\\.", "/"));
		
		File classDir = new File(url.getFile());
		
		for (File file: classDir.listFiles()) {
			if(file.isDirectory()){
				doScanner(packageName+"."+file.getName());
			}else{
				classNames.add(packageName + "." + file.getName().replaceAll(".class", ""));
			}
		}
		
		
	}
	
	
	/**
	 * 首字母小写的方法
	 * @param str
	 * @return
	 */
	private String lowerFirstCase(String str){
		char[] chars = str.toCharArray();
		chars[0] += 32;
		return String.valueOf(chars);
	}


	
	private class Handler{
		
		protected Object controller;
		
		protected Method method;

		public Object getController() {
			return controller;
		}

		public void setController(Object controller) {
			this.controller = controller;
		}

		public Method getMethod() {
			return method;
		}

		public void setMethod(Method method) {
			this.method = method;
		}
		
		
	}
	

	
	
}
