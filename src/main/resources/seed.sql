-- Video cue: "This sample data gives me two tutors, two subjects, four future
-- slots, and one booking. Slot 2 is booked; the others should appear as open."
INSERT INTO users(user_id,full_name,email,role) VALUES
 (1,'Maya Student','maya@example.test','CUSTOMER'),
 (2,'Alex Chen','alex@example.test','PROVIDER'),
 (3,'Sam Rivera','sam@example.test','PROVIDER');
INSERT INTO providers(provider_id,user_id,bio) VALUES
 (1,2,'Java and software design tutor'), (2,3,'Databases and SQL tutor');
INSERT INTO services(service_id,name,description) VALUES
 (1,'Java Fundamentals','One-hour Java tutoring'),
 (2,'SQL and Databases','One-hour SQL tutoring');
-- Relative dates keep the example useful whenever the application is started.
INSERT INTO availability_slots(slot_id,provider_id,service_id,starts_at,ends_at) VALUES
 (1,1,1,DATEADD('HOUR',10,DATEADD('DAY',1,CURRENT_DATE)),DATEADD('HOUR',11,DATEADD('DAY',1,CURRENT_DATE))),
 (2,1,1,DATEADD('HOUR',11,DATEADD('DAY',1,CURRENT_DATE)),DATEADD('HOUR',12,DATEADD('DAY',1,CURRENT_DATE))),
 (3,2,2,DATEADD('HOUR',10,DATEADD('DAY',1,CURRENT_DATE)),DATEADD('HOUR',11,DATEADD('DAY',1,CURRENT_DATE))),
 (4,2,2,DATEADD('HOUR',14,DATEADD('DAY',2,CURRENT_DATE)),DATEADD('HOUR',15,DATEADD('DAY',2,CURRENT_DATE)));
INSERT INTO appointments(appointment_id,customer_id,slot_id,status) VALUES (1,1,2,'BOOKED');
-- Explicit fixture IDs must not collide with later generated IDs.
ALTER TABLE users ALTER COLUMN user_id RESTART WITH 4;
ALTER TABLE providers ALTER COLUMN provider_id RESTART WITH 3;
ALTER TABLE services ALTER COLUMN service_id RESTART WITH 3;
ALTER TABLE availability_slots ALTER COLUMN slot_id RESTART WITH 5;
ALTER TABLE appointments ALTER COLUMN appointment_id RESTART WITH 2;
