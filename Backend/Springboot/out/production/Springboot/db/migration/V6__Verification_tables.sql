-- Add ID card columns to users table
ALTER TABLE users ADD COLUMN IF NOT EXISTS id_card_path VARCHAR(500);
ALTER TABLE users ADD COLUMN IF NOT EXISTS id_card_uploaded_at TIMESTAMP;
ALTER TABLE users ADD COLUMN IF NOT EXISTS verification_requested_at TIMESTAMP;
ALTER TABLE users ADD COLUMN IF NOT EXISTS verification_reviewed_by UUID REFERENCES users(id);
ALTER TABLE users ADD COLUMN IF NOT EXISTS verification_reviewed_at TIMESTAMP;
ALTER TABLE users ADD COLUMN IF NOT EXISTS verification_rejection_reason TEXT;

-- Create audit log for verification actions
CREATE TABLE IF NOT EXISTS verification_audit_logs (
                                                       id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id),
    admin_id UUID REFERENCES users(id),
    action VARCHAR(50) NOT NULL, -- UPLOAD, APPROVE, REJECT, VIEW_DECRYPT
    ip_address INET,
    details TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
    );

CREATE INDEX IF NOT EXISTS idx_verification_audit_user ON verification_audit_logs(user_id);
CREATE INDEX IF NOT EXISTS idx_verification_audit_admin ON verification_audit_logs(admin_id);