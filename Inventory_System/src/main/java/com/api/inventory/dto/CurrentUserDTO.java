// src/main/java/com/api/inventory/dto/CurrentUserFull.java

package com.api.inventory.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor

public class CurrentUserDTO {
	@JsonProperty("email")
    private String email;
	@JsonProperty("name")
    private String name;
	@JsonProperty("phone")
    private String phone;
	@JsonProperty("role")
    private String role;
	private Long roleId; 

	/** What this person may do (from their role, in the database). The screens show and hide tools with it. */
	private java.util.List<String> permissions = java.util.List.of();

	public java.util.List<String> getPermissions() { return permissions; }
	public void setPermissions(java.util.List<String> permissions) { this.permissions = permissions; }


    public CurrentUserDTO(String email, String name, String phone, String role) {
        this.email = email;
        this.name = name;
        this.phone = phone;
        this.role = role;
        
    }

	public String getEmail() {
		return email;
	}

	public void setEmail(String email) {
		this.email = email;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getPhone() {
		return phone;
	}

	public void setPhone(String phone) {
		this.phone = phone;
	}

	public String getRole() {
		return role;
	}

	public void setRole(String role) {
		this.role = role;
	}

	public Long getRoleId() {
		return roleId;
	}

	public void setRoleId(Long roleId) {
		this.roleId = roleId;
	}

   
}