package com.api.inventory.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public class SignupRequest {
    private String name;      // Full name
    private String email;     // Login ID
    private String phone;
    private String password;
    /** The version of the Terms of Use and Privacy the person ticked "I agree" to. */
    private Integer acceptedTermsVersion;

    public Integer getAcceptedTermsVersion() { return acceptedTermsVersion; }
    public void setAcceptedTermsVersion(Integer acceptedTermsVersion) { this.acceptedTermsVersion = acceptedTermsVersion; }
    // getters/setters
	public String getName() {
		return name;
	}
	public void setName(String name) {
		this.name = name;
	}
	public String getEmail() {
		return email;
	}
	public void setEmail(String email) {
		this.email = email;
	}
	public String getPhone() {
		return phone;
	}
	public void setPhone(String phone) {
		this.phone = phone;
	}
	public String getPassword() {
		return password;
	}
	public void setPassword(String password) {
		this.password = password;
	}
    
    
}