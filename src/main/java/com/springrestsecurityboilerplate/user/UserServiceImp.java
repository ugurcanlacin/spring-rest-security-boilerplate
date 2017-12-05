package com.springrestsecurityboilerplate.user;

import java.util.Calendar;
import java.util.Date;
import java.util.UUID;

import org.springframework.amqp.core.AmqpTemplate;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.WebRequest;

import com.springrestsecurityboilerplate.Mailer;
import com.springrestsecurityboilerplate.OnRegistrationCompleteEvent;
import com.springrestsecurityboilerplate.ResendToken;
import com.springrestsecurityboilerplate.VerificationToken;
import com.springrestsecurityboilerplate.VerificationTokenRepository;
import com.springrestsecurityboilerplate.validation.EmailExistsException;
import com.springrestsecurityboilerplate.validation.UsernameExistsException;

@Service
public class UserServiceImp implements UserService {

	@Autowired
	UserRepository userRepository;

	@Autowired
	ApplicationEventPublisher eventPublisher;

	@Autowired
	VerificationTokenRepository tokenRepository;

	// @Autowired
	// Mailer mailer;

	@Autowired
	private AmqpTemplate amqpTemplate;

	@Autowired
	private RabbitTemplate template;

	@Override
	public void registerUser(User user, WebRequest request) throws EmailExistsException, UsernameExistsException {

		if (isEmailExist(user.getEmail())) {
			// System.out.println("Existed email");
			throw new EmailExistsException(user.getEmail());
		} else if (isUsernameExist(user.getUsername())) {
			throw new UsernameExistsException(user.getUsername());
		}

		else {
			user.setCreationDate(new Date());
			user.setIsActive(false);
			user.setActivationDate(null);

			userRepository.save(user);
			// eventPublisher.publishEvent(new
			// OnRegistrationCompleteEvent(user));
			String appUrl = request.getContextPath();
			eventPublisher.publishEvent(new OnRegistrationCompleteEvent(user, request.getLocale(), appUrl));
			System.out.println("Registered!");
		}
	}

	private boolean isEmailExist(String email) {
		User user = userRepository.findByEmail(email);

		boolean isUserExistByEmail = user != null;
		return isUserExistByEmail;
	}

	private boolean isUsernameExist(String username) {
		User user = userRepository.findByUsername(username);

		boolean isUserExistByUsername = user != null;
		return isUserExistByUsername;
	}

	@Override
	public User getUser(String verificationToken) {
		User user = tokenRepository.findByToken(verificationToken).getUser();
		return user;
	}

	@Override
	public VerificationToken getVerificationToken(String VerificationToken) {
		return tokenRepository.findByToken(VerificationToken);
	}

	@Override
	public void createVerificationToken(User user, String token) {
		VerificationToken myToken = new VerificationToken(user, token);
		user.setToken(myToken);
		tokenRepository.save(myToken);
	}

	@Override
	public void updateUser(User user) {
		userRepository.save(user);

	}

	@Override
	public void verifyToken(String token) {

		VerificationToken verificationToken = getVerificationToken(token);

		if (verificationToken == null) {

			System.out.println("invalid token");

		} else {
			User user = verificationToken.getUser();
			Calendar cal = Calendar.getInstance();
			if ((verificationToken.getExpiryDate().getTime() - cal.getTime().getTime()) <= 0) {
				System.out.println("Expired token!");
			} else {

				if (user.getIsActive() == true) {
					System.out.println("This user is already active");
				}

				else {

					user.setActivationDate(new Date());
					user.setIsActive(true);
					user.setToken(null);
					updateUser(user);
					tokenRepository.delete(verificationToken);

				}
			}

		}

	}

	@Override
	public void resendTokenByEmail(String email) {

		User user = userRepository.findByEmail(email);

		if (user != null && user.getIsActive() == false) {
			String token = UUID.randomUUID().toString();
			createResendVerificationToken(user, token);

		} else {
			System.out.println("There is no account with that e-mail or User is already active");
		}

	}

	@Override
	public void createResendVerificationToken(User user, String token) {

		VerificationToken oldToken = user.getToken();
		oldToken.updateToken(token);
		tokenRepository.save(oldToken);
		ResendToken resendToken = new ResendToken(user, oldToken);
		// amqpTemplate.convertAndSend("email-exchange", "resend-token",
		// resendToken);
		template.convertAndSend("email-direct", "resend-token", resendToken);
		// mailer.resendVerificationToken(user, oldToken);
	}

}