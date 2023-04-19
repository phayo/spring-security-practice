package com.jdriven.leaverequest;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

import static com.jdriven.leaverequest.LeaveRequest.Status.APPROVED;
import static com.jdriven.leaverequest.LeaveRequest.Status.DENIED;
import static com.jdriven.leaverequest.LeaveRequest.Status.PENDING;
import static java.time.LocalDate.of;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.http.HttpStatus.ACCEPTED;
import static org.springframework.http.HttpStatus.NO_CONTENT;
import static org.springframework.http.HttpStatus.OK;
import static org.springframework.http.MediaType.APPLICATION_JSON;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class LeaveRequestControllerSpringBootWebEnvRandomPortTest {

	private static final ParameterizedTypeReference<List<LeaveRequestDTO>> TYPE_REFERENCE = new ParameterizedTypeReference<List<LeaveRequestDTO>>() {
	};

	@Autowired
	private LeaveRequestRepository repository;

	@Autowired
	private TestRestTemplate restTemplate;

	@BeforeEach
	void beforeEach() {
		repository.clear();
	}

	@MockBean
	private JwtDecoder jwtDecoder;

	@Nested
	class AuthorizeUser {

		@BeforeEach
		void beforeEach() {
			// TODO Ensure below requests are executed as user alice
			when(jwtDecoder.decode(anyString()))
					.thenReturn(Jwt.withTokenValue("token")
							.subject("alice")
							.header("alg", "none")
							.build());
		}

		@Test
		void testRequest() {
			HttpHeaders headers = new HttpHeaders();
			headers.setBearerAuth("some.random.token");
			HttpEntity<?> httpEntity = new HttpEntity<>(headers);

			LocalDate from = of(2022, 11, 30);
			LocalDate to = of(2022, 12, 3);
			// XXX Authenticate as alice when making this request
			ResponseEntity<LeaveRequestDTO> response = restTemplate.exchange(
					"/request/{employee}?from={from}&to={to}", HttpMethod.POST,
					httpEntity, LeaveRequestDTO.class, "alice", from, to);
			assertThat(response.getStatusCode()).isEqualByComparingTo(ACCEPTED);
			assertThat(response.getHeaders().getContentType()).isEqualByComparingTo(APPLICATION_JSON);
			assertThat(response.getBody().getEmployee()).isEqualTo("alice");
			assertThat(response.getBody().getFromDate()).isEqualTo(from);
			assertThat(response.getBody().getToDate()).isEqualTo(to);
			assertThat(response.getBody().getStatus()).isEqualByComparingTo(PENDING);
		}

		@Test
		void testViewRequest() {
			HttpHeaders headers = new HttpHeaders();
			headers.setBearerAuth("some.random.token");
			HttpEntity<?> httpEntity = new HttpEntity<>(headers);

			LeaveRequest saved = repository.save(new LeaveRequest("alice", of(2022, 11, 30), of(2022, 12, 3), PENDING));
			// XXX Authenticate as alice when making this request
			ResponseEntity<LeaveRequestDTO> response = restTemplate.exchange("/view/request/{id}", GET, httpEntity,
					LeaveRequestDTO.class, saved.getId());
			assertThat(response.getStatusCode()).isEqualByComparingTo(OK);
			assertThat(response.getHeaders().getContentType()).isEqualByComparingTo(APPLICATION_JSON);
			assertThat(response.getBody().getEmployee()).isEqualTo("alice");
			assertThat(response.getBody().getStatus()).isEqualByComparingTo(PENDING);
		}

		@Test
		void testViewEmployee() {
			HttpHeaders headers = new HttpHeaders();
			headers.setBearerAuth("some.random.token");
			HttpEntity<?> httpEntity = new HttpEntity<>(headers);

			repository.save(new LeaveRequest("alice", of(2022, 11, 30), of(2022, 12, 3), PENDING));
			// XXX Authenticate as alice when making this request
			ResponseEntity<List<LeaveRequestDTO>> response = restTemplate.exchange("/view/employee/{employee}", GET,
					httpEntity,
					TYPE_REFERENCE, "alice");
			assertThat(response.getStatusCode()).isEqualByComparingTo(OK);
			assertThat(response.getHeaders().getContentType()).isEqualByComparingTo(APPLICATION_JSON);
			assertThat(response.getBody().get(0).getEmployee()).isEqualTo("alice");
			assertThat(response.getBody().get(0).getStatus()).isEqualByComparingTo(PENDING);
		}
	}

	@Nested
	class AuthorizeRole {

		@BeforeEach
		void beforeEach() {
			// TODO Ensure below requests are executed as user with role HR
			when(jwtDecoder.decode(anyString())).thenReturn(Jwt.withTokenValue("token")
					.subject("Bob")
					.claim("realm_access", Collections.singletonMap("roles", Collections.singletonList("HR")))
					.header("alg", "none")
					.claim("realm_access", Collections.singletonMap("roles", Collections.singletonList("HR")))
					.build());
		}

		@Test
		void testApprove() {
			HttpHeaders headers = new HttpHeaders();
			headers.setBearerAuth("some.random.token");
			HttpEntity<?> httpEntity = new HttpEntity<>(headers);

			LeaveRequest saved = repository.save(new LeaveRequest("alice", of(2022, 11, 30), of(2022, 12, 3), PENDING));
			// XXX Authenticate with HR role when making this request
			ResponseEntity<LeaveRequestDTO> response = restTemplate.exchange("/approve/{id}", POST, httpEntity,
					LeaveRequestDTO.class, saved.getId());
			assertThat(response.getStatusCode()).isEqualByComparingTo(ACCEPTED);
			assertThat(response.getHeaders().getContentType()).isEqualByComparingTo(APPLICATION_JSON);
			assertThat(response.getBody().getEmployee()).isEqualTo("alice");
			assertThat(response.getBody().getStatus()).isEqualByComparingTo(APPROVED);
		}

		@Test
		void testApproveMissing() {
			// XXX Authenticate with HR role when making this request
			HttpHeaders headers = new HttpHeaders();
			headers.setBearerAuth("some.random.token");
			HttpEntity<?> httpEntity = new HttpEntity<>(headers);

			ResponseEntity<LeaveRequestDTO> response = restTemplate.exchange("/approve/{id}", POST, httpEntity,
					LeaveRequestDTO.class, UUID.randomUUID());
			assertThat(response.getStatusCode()).isEqualByComparingTo(NO_CONTENT);
		}

		@Test
		void testDeny() {
			LeaveRequest saved = repository.save(new LeaveRequest("alice", of(2022, 11, 30), of(2022, 12, 3), PENDING));
			// XXX Authenticate with HR role when making this request
			HttpHeaders headers = new HttpHeaders();
			headers.setBearerAuth("some.random.token");
			HttpEntity<?> httpEntity = new HttpEntity<>(headers);

			ResponseEntity<LeaveRequestDTO> response = restTemplate.exchange("/deny/{id}", POST, httpEntity,
					LeaveRequestDTO.class, saved.getId());
			assertThat(response.getStatusCode()).isEqualByComparingTo(ACCEPTED);
			assertThat(response.getHeaders().getContentType()).isEqualByComparingTo(APPLICATION_JSON);
			assertThat(response.getBody().getEmployee()).isEqualTo("alice");
			assertThat(response.getBody().getStatus()).isEqualByComparingTo(DENIED);
		}

		@Test
		void testViewRequestMissing() {
			// XXX Authenticate with HR role when making this request
			HttpHeaders headers = new HttpHeaders();
			headers.setBearerAuth("some.random.token");
			HttpEntity<?> httpEntity = new HttpEntity<>(headers);

			ResponseEntity<LeaveRequestDTO> response = restTemplate.exchange("/view/request/{id}", GET, httpEntity,
					LeaveRequestDTO.class,
					UUID.randomUUID());
			assertThat(response.getStatusCode()).isEqualByComparingTo(NO_CONTENT);
		}

		@Test
		void testViewAll() {
			repository.save(new LeaveRequest("alice", of(2022, 11, 30), of(2022, 12, 3), PENDING));
			// XXX Authenticate with HR role when making this request
			HttpHeaders headers = new HttpHeaders();
			headers.setBearerAuth("some.random.token");
			HttpEntity<?> httpEntity = new HttpEntity<>(headers);

			ResponseEntity<List<LeaveRequestDTO>> response = restTemplate.exchange("/view/all", GET, httpEntity,
					TYPE_REFERENCE);
			assertThat(response.getStatusCode()).isEqualByComparingTo(OK);
			assertThat(response.getHeaders().getContentType()).isEqualByComparingTo(APPLICATION_JSON);
			assertThat(response.getBody().get(0).getEmployee()).isEqualTo("alice");
			assertThat(response.getBody().get(0).getStatus()).isEqualByComparingTo(PENDING);
		}
	}
}
