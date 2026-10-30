---
title: Logging in
covers:
  screens: [SCR-001, SCR-003, SCR-004, SCR-005]
  stories: [US-001, US-005, US-006, US-009, US-010, US-011, US-021, US-022, US-023, US-024, US-025, US-026, US-027, US-029, US-030]
---

# Logging in

Open the web interface and choose *Log in*. The page *Log in to OEA* offers one button *Log in with …* per identity provider of your organization and, below it, the login with the local system admin account.

## Log in through your identity provider

1. Choose *Log in with* and the name of your provider.
2. Log in at your provider as usual; it sends you back to OEA.
3. You are logged in. The header shows *Catalogs*, your profile and *Log out*.

Your administrator must have created your person and bound it to your identity at the provider. If that is not the case, the login page shows *Login failed* – for safety, without the exact reason; your administrator finds it in the audit log. If the provider is not reachable, the page asks you to try again later.

Your roles come from your groups at the provider. Roles with administrative permissions apply only if you logged in with a second factor at the provider.

## Log in with the local system admin account

Enter the login name and password of the system admin account and choose *Log in*. After the setup phase, the page asks for the code of your authenticator app: enter it and choose *Confirm*. *Start again* returns to the password step.

<Screenshot name="login" alt="Login page with the providers and the login of the local system admin account" />

- After five wrong codes, you have to start again with the password.
- After repeated wrong passwords, further attempts are delayed (at most 15 minutes).
- Every login and every failed attempt – through a provider or with the local account – is recorded in the audit log with time, mechanism and result (`auth.login`, `auth.login-failed`); a failed attempt names the internal reason only there.

## Log in with your e-mail address and password

If your organization runs OEA without an identity provider, your administrator gives you an initial password. Enter your e-mail address and this password in the local form of the login page.

1. After the first login, the page *Change password* opens. Enter the initial password as current password and choose your own password twice. Until then, nothing else is available.
2. From then on, log in with your e-mail address and your own password. You can change it again under `/me/password`; this ends your other sessions.

- Your password must meet the password policy of your organization: at least 12 characters, and possibly a longer minimum, certain character types, or a password you have not used recently. If it does not, the page lists every rule it misses.
- *Suggest a password* shows a generated password that meets the policy. Store it in your password manager, then use it with *Use as new password*. OEA does not keep the suggestion.
- If your organization lets passwords expire, the page *Change password* opens at the first login after the expiry, as after an initial password.
- The initial password expires after the time your administrator chose (default 72 hours). An expired initial password fails like a wrong one; ask your administrator for a new one.
- If you forgot your password, your administrator resets it and gives you a temporary password. Your old password and all your sessions end at once; at the next login, you choose a new password.
- A failed login never tells whether the account exists, and every failed attempt takes the same time.
- Roles with administrative permissions need a second factor. They apply only after a login with password and code from your authenticator app (see [Set up a second factor](#set-up-a-second-factor)).
- If you have set up an authenticator app, the login asks for its code after the password. A wrong code fails like a wrong password; after five wrong codes, start again with the password.

## Log in with a passkey {#log-in-with-a-passkey}

Once you have set up a passkey (see [Set up a second factor](#set-up-a-second-factor)), choose *Log in with a passkey* on the login page and confirm with the fingerprint, face or PIN of your device. You enter neither your e-mail address nor your password.

- A passkey login counts as confirmed with a second factor; roles with administrative permissions apply.
- If you log in with your password and have a passkey, the login asks you to confirm with *Use a passkey* – or with the code of your authenticator app, if you have one.
- A passkey that is not set up at OEA, or a cancelled confirmation, fails like a wrong password.
- If your administrator reset your password, you still choose a new password after the passkey login.

## Your profile

*Profile* (`/me`) shows your name, the account type, whether your session is confirmed with a second factor, your roles and your authenticator apps. If no role is assigned to you yet, the profile tells you so; ask an administrator for access.

## Set up a second factor {#set-up-a-second-factor}

A second factor is a passkey on your device (fingerprint, face or PIN) or an authenticator app (any app for time-based one-time passwords, TOTP) on your phone. The local system admin account uses an authenticator app and needs one to end the setup phase (see [First administrator](../setup/first-administrator)). If you log in with your e-mail address and password, OEA offers a second factor after the login as long as you have none; choose *Skip for now* to set it up later.

1. In *Profile*, choose *Set up a second factor* (or *Add a second factor*). Choose *Passkey (recommended)* or *Authenticator app*; the choice appears only in browsers that support passkeys. Optionally, give it a name, for example the device; without a name it is called "Passkey" or "Authenticator app" with the date.
2. For a passkey, choose *Set up passkey* and confirm in the dialog of your browser or device. For an authenticator app, choose *Set up authenticator app* and scan the QR code with the app.
3. For an authenticator app, enter the code from the app under *Code from the app* and choose *Confirm*.

<Screenshot name="second-factor-enroll" alt="Setting up the authenticator app with QR code and code field" />

The page confirms that the second factor is set up and the session is confirmed. Setting up a second factor needs a login within the last 5 minutes; otherwise, the page asks you to log in again.

- You can set up several passkeys and authenticator apps, for example on a backup phone. At the login, any of them works.
- *Profile* lists your second factors with their type and the date they were added and last used. *Remove* takes one away, for example of a lost phone; this also needs a login within the last 5 minutes and ends your other sessions. The local system admin account keeps at least one after the setup phase.
- If you lost every device, your administrator resets your second factors. You then log in with your password alone and set up a new second factor.

## Sessions and logging out

Choose *Log out* in the header or in your profile. A session also ends after 30 minutes without activity and at the latest 12 hours after the login.
