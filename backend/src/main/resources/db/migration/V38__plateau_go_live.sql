-- The go-live of a plateau (us-086-plateau-go-live D1; US-088, US-157): the transition sets the
-- status and the validity of the new baseline and the realized one, and the solutions of plateaus
-- that followed the old baseline start from the new one.

GRANT UPDATE (status, valid_from, valid_to) ON plateaus TO oea_app;
GRANT UPDATE (from_plateau_id) ON solutions TO oea_app;
