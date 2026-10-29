---
title: Logging in
covers:
  screens: []
  stories: [US-001, US-005]
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

## Your profile

*Profile* (`/me`) shows your name, the account type, whether your session is confirmed with a second factor, and your roles. If no role is assigned to you yet, the profile tells you so; ask an administrator for access.

## Set up a second factor

The local system admin account needs a second factor to end the setup phase (see [First administrator](../setup/first-administrator)):

1. In *Profile*, choose *Set up a second factor*, then *Set up authenticator app*.
2. Scan the QR code with an authenticator app (any app for time-based one-time passwords, TOTP).
3. Enter the code from the app under *Code from the app* and choose *Confirm*.

<Screenshot name="second-factor-enroll" alt="Setting up the authenticator app with QR code and code field" />

The page confirms that the authenticator app is set up and the session is confirmed. Setting up a second factor needs a login within the last 5 minutes; otherwise, the page asks you to log in again.

## Sessions and logging out

Choose *Log out* in the header or in your profile. A session also ends after 30 minutes without activity and at the latest 12 hours after the login.
