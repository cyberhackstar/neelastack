-- Neelastack platform - Cloudinary authenticated (signed) file delivery
-- Files uploaded before this migration were stored with Cloudinary's default
-- public delivery type and will need to be re-uploaded to benefit from signed access.

ALTER TABLE project_files ADD COLUMN cloudinary_resource_type VARCHAR(20) NOT NULL DEFAULT 'raw';
ALTER TABLE project_files ALTER COLUMN cloudinary_resource_type DROP DEFAULT;
