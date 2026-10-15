package com.mosbyboys.dishesbecrazy.ui.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController //Tells Spring that this class handles HTTP requests and that its methods return response data
public class AuthController {

	@GetMapping("/register") //Associates an HTTP GET request for that URL with the sayHello() method
	public String sayHello(){
		return null;
	}

}
