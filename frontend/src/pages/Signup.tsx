import React, { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import axios from 'axios';

type FormFields = 'firstName' | 'lastName' | 'username' | 'email' | 'password' | 'confirmPassword';
type FieldErrors = Partial<Record<FormFields, string>>;

function validate(fields: Record<FormFields, string>): FieldErrors {
  const errs: FieldErrors = {};

  if (!fields.firstName.trim()) {
    errs.firstName = 'First name is required.';
  } else if (!/^[A-Za-z\s'-]{1,50}$/.test(fields.firstName.trim())) {
    errs.firstName = 'First name may only contain letters.';
  }

  if (!fields.lastName.trim()) {
    errs.lastName = 'Last name is required.';
  } else if (!/^[A-Za-z\s'-]{1,50}$/.test(fields.lastName.trim())) {
    errs.lastName = 'Last name may only contain letters.';
  }

  if (!fields.username.trim()) {
    errs.username = 'Username is required.';
  } else if (fields.username.length < 3) {
    errs.username = 'Username must be at least 3 characters.';
  } else if (!/^[A-Za-z0-9_]{3,30}$/.test(fields.username)) {
    errs.username = 'Username may only contain letters, numbers, and underscores.';
  }

  if (!fields.email.trim()) {
    errs.email = 'Email is required.';
  } else if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(fields.email)) {
    errs.email = 'Enter a valid email address.';
  }

  if (!fields.password) {
    errs.password = 'Password is required.';
  } else if (fields.password.length < 8) {
    errs.password = 'Password must be at least 8 characters.';
  } else if (!/[A-Z]/.test(fields.password)) {
    errs.password = 'Password must contain at least one uppercase letter.';
  } else if (!/[0-9]/.test(fields.password)) {
    errs.password = 'Password must contain at least one number.';
  }

  if (!fields.confirmPassword) {
    errs.confirmPassword = 'Please confirm your password.';
  } else if (fields.password !== fields.confirmPassword) {
    errs.confirmPassword = 'Passwords do not match.';
  }

  return errs;
}

const empty: Record<FormFields, string> = {
  firstName: '', lastName: '', username: '', email: '', password: '', confirmPassword: '',
};

const Signup: React.FC = () => {
  const [formData, setFormData] = useState(empty);
  const [fieldErrors, setFieldErrors] = useState<FieldErrors>({});
  const [showPassword, setShowPassword] = useState(false);
  const [error, setError] = useState('');
  const navigate = useNavigate();

  const handleChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const { name, value } = e.target;
    setFormData(prev => ({ ...prev, [name]: value }));
    if (fieldErrors[name as FormFields]) {
      setFieldErrors(prev => ({ ...prev, [name]: undefined }));
    }
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    const errs = validate(formData);
    if (Object.keys(errs).length > 0) {
      setFieldErrors(errs);
      return;
    }
    setError('');
    try {
      const { confirmPassword: _, ...payload } = formData;
      await axios.post('http://localhost:9000/api/v1/auth/signup', payload);
      navigate('/login');
    } catch (err: any) {
      const msg: string = err.response?.data?.message || '';
      if (msg.toLowerCase().includes('username')) {
        setFieldErrors(prev => ({ ...prev, username: 'This username is already taken.' }));
      } else if (msg.toLowerCase().includes('email')) {
        setFieldErrors(prev => ({ ...prev, email: 'An account with this email already exists.' }));
      } else {
        setError(msg || 'Registration failed. Please try again.');
      }
    }
  };

  const field = (name: FormFields, placeholder: string, type = 'text') => (
    <div className="signup-field">
      <input
        type={type}
        name={name}
        placeholder={placeholder}
        value={formData[name]}
        onChange={handleChange}
        className={fieldErrors[name] ? 'input-error' : ''}
        autoComplete={type === 'password' ? 'new-password' : undefined}
      />
      {fieldErrors[name] && <span className="field-error-msg">{fieldErrors[name]}</span>}
    </div>
  );

  return (
    <div className="auth-container">
      <div className="auth-card">
        <Link to="/" className="logo-text">SpringHi.ai</Link>
        <h2>Create your account</h2>
        <p>Join the future of AI investing.</p>

        {error && <div className="error-msg">{error}</div>}

        <form onSubmit={handleSubmit} noValidate>
          <div className="input-group">
            {field('firstName', 'First Name')}
            {field('lastName', 'Last Name')}
          </div>
          {field('username', 'Username')}
          <p className="password-hint">3–30 characters, letters, numbers, and underscores only.</p>
          {field('email', 'Email', 'email')}
          <div className="signup-field">
            <div className="password-input-wrap">
              <input
                type={showPassword ? 'text' : 'password'}
                name="password"
                placeholder="Password"
                value={formData.password}
                onChange={handleChange}
                className={fieldErrors.password ? 'input-error' : ''}
                autoComplete="new-password"
              />
              <button type="button" className="show-password-btn" onClick={() => setShowPassword(v => !v)}>
                {showPassword ? 'Hide' : 'Show'}
              </button>
            </div>
            {fieldErrors.password && <span className="field-error-msg">{fieldErrors.password}</span>}
          </div>
          <p className="password-hint">Min. 8 characters, at least one uppercase letter and one number.</p>
          <div className="signup-field">
            <div className="password-input-wrap">
              <input
                type={showPassword ? 'text' : 'password'}
                name="confirmPassword"
                placeholder="Confirm Password"
                value={formData.confirmPassword}
                onChange={handleChange}
                className={fieldErrors.confirmPassword ? 'input-error' : ''}
                autoComplete="new-password"
              />
            </div>
            {fieldErrors.confirmPassword && <span className="field-error-msg">{fieldErrors.confirmPassword}</span>}
          </div>
          <button type="submit" className="btn-primary-full">Sign Up</button>
        </form>

        <p className="auth-footer">
          Already have an account? <Link to="/login">Log in</Link>
        </p>
      </div>
    </div>
  );
};

export default Signup;
