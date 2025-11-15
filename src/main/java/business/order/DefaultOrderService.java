package business.order;

import api.ApiException;
import business.BookstoreDbException;
import business.JdbcUtils;
import business.book.Book;
import business.book.BookDao;
import business.cart.ShoppingCart;
import business.cart.ShoppingCartItem;
import business.customer.Customer;
import business.customer.CustomerDao;
import business.customer.CustomerForm;
import business.book.BookForm;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.DateTimeException;
import java.time.YearMonth;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class DefaultOrderService implements OrderService {

	private BookDao bookDao;
	private OrderDao orderDao;
	private LineItemDao lineItemDao;

	private CustomerDao customerDao;


	public void setBookDao(BookDao bookDao) {
		this.bookDao = bookDao;
	}

	public void setLineItemDao(LineItemDao lineItemDao) {
		this.lineItemDao = lineItemDao;
	}

	public void setCustomerDao(CustomerDao customerDao) {
		this.customerDao = customerDao;
	}

	public void setOrderDao(OrderDao orderDao) {
		this.orderDao = orderDao;
	}

	@Override
	public OrderDetails getOrderDetails(long orderId) {
		Order order = orderDao.findByOrderId(orderId);
		Customer customer = customerDao.findByCustomerId(order.customerId());
		List<LineItem> lineItems = lineItemDao.findByOrderId(orderId);
		List<Book> books = lineItems
				.stream()
				.map(lineItem -> bookDao.findByBookId(lineItem.bookId()))
				.toList();
		return new OrderDetails(order, customer, lineItems, books);
	}

	@Override
    public long placeOrder(CustomerForm customerForm, ShoppingCart cart) {

		validateCustomer(customerForm);
		validateCart(cart);

		try (Connection connection = JdbcUtils.getConnection()) {
			Date ccExpDate = getCardExpirationDate(
					customerForm.getCcExpiryMonth(),
					customerForm.getCcExpiryYear());
			return performPlaceOrderTransaction(
					customerForm.getName(),
					customerForm.getAddress(),
					customerForm.getPhone(),
					customerForm.getEmail(),
					customerForm.getCcNumber(),
					ccExpDate, cart, connection);
		} catch (SQLException e) {
			throw new BookstoreDbException("Error during close connection for customer order", e);
		}
	}

	private Date getCardExpirationDate(String monthString, String yearString) {
		return new Date(); // TODO Implement this correctly
	}

	private long performPlaceOrderTransaction(
			String name, String address, String phone,
			String email, String ccNumber, Date date,
			ShoppingCart cart, Connection connection) {
		try {
			connection.setAutoCommit(false);
			long customerId = customerDao.create(
					connection, name, address, phone, email,
					ccNumber, date);
			long customerOrderId = orderDao.create(
					connection,
					cart.getComputedSubtotal() + cart.getSurcharge(),
					generateConfirmationNumber(), customerId);
			for (ShoppingCartItem item : cart.getItems()) {
				lineItemDao.create(connection, customerOrderId,
						item.getBookId(), item.getQuantity());
			}
			connection.commit();
			return customerOrderId;
		} catch (Exception e) {
			try {
				connection.rollback();
			} catch (SQLException e1) {
				throw new BookstoreDbException("Failed to roll back transaction", e1);
			}
			return 0;
		}
	}

	private int generateConfirmationNumber() {
		return ThreadLocalRandom.current().nextInt(999999999);
	}

	private boolean phoneIsValid(String phone) {

		if (phone == null || phone.equals("")) return false;

		// get rid of parens, spaces, and dashes
		phone = phone.replaceAll(" ", "");
		phone = phone.replaceAll("-", "");
		phone = phone.replaceAll("\\(", "");
		phone = phone.replaceAll("\\)", "");

		// should have a number (no letters) with exactly 10 digits
		if (!phone.matches("[\\d]+") || phone.length() != 10) return false;
		return true;
	}
	private boolean emailIsValid(String email){

		if (email == null || email.equals("")) return false;
		if (email.contains(" ") || !email.contains("@") || email.endsWith(".")) return false;
		return true;
	}

	private boolean ccNumberIsValid(String ccNumber){

		if (ccNumber == null || ccNumber.equals("")) return false;

		ccNumber = ccNumber.replaceAll(" ", "");
		ccNumber = ccNumber.replaceAll("-", "");

		if (ccNumber.length() < 14 || ccNumber.length() > 16) return false;
		return true;
	}

	private void validateCustomer(CustomerForm customerForm) {

    	String name = customerForm.getName();

		if (name == null || name.equals("") || name.length() < 4 || name.length() > 45) {
			throw new ApiException.ValidationFailure("Invalid name field");
		}

		String address = customerForm.getAddress();

		if (address == null || address.equals("") || address.length()<4 || address.length() > 45) {
			throw new ApiException.ValidationFailure("Invalid address ");
		}

		String phone = customerForm.getPhone();
		if(!phoneIsValid(phone)){
			throw new ApiException.ValidationFailure("Invalid phone number");
		}
		String email = customerForm.getEmail();

		if(!emailIsValid(email)){
			throw new ApiException.ValidationFailure("Invalid email");
		}
		String ccNumber = customerForm.getCcNumber();

		if(!ccNumberIsValid(ccNumber)){
			throw new ApiException.ValidationFailure("Invalid ccNumber field");
		}

		// TODO: Validation checks for address, phone, email, ccNumber

		if (expiryDateIsInvalid(customerForm.getCcExpiryMonth(), customerForm.getCcExpiryYear())) {
			throw new ApiException.ValidationFailure("Please enter a valid expiration date.");

		}
	}

	private boolean expiryDateIsInvalid(String ccExpiryMonth, String ccExpiryYear) {

		// TODO: return true when the provided month/year is before the current month/yeaR
		// HINT: Use Integer.parseInt and the YearMonth class
		if (ccExpiryMonth == null || ccExpiryYear == null || ccExpiryMonth.equals("") || ccExpiryYear.equals("")) return true;

		int ccExpiryMonthVal = Integer.parseInt(ccExpiryMonth);
		int ccExpiryYearVal = Integer.parseInt(ccExpiryYear);

		int currMonth = YearMonth.now().getMonthValue();
		int currYear = YearMonth.now().getYear();

		if (ccExpiryMonthVal > 12 || ccExpiryMonthVal < 1 || ccExpiryMonthVal < currMonth && ccExpiryYearVal == currYear) return true;

		if (ccExpiryYearVal < currYear) return true;

		return false;

	}

	private void validateCart(ShoppingCart cart) {

		if (cart.getItems().size() <= 0) {
			throw new ApiException.ValidationFailure("Cart is empty.");
		}

		cart.getItems().forEach(item-> {
			if (item.getQuantity() < 0 || item.getQuantity() > 99) {
				throw new ApiException.ValidationFailure("Invalid quantity");
			}
			Book databaseBook = bookDao.findByBookId(item.getBookId());
			// TODO: complete the required validations
			if (item.getBookForm().getPrice() != databaseBook.getPrice()){
				throw new ApiException.ValidationFailure("Invalid book price.");
			}

			if (item.getBookForm().getCategoryId() != databaseBook.getCategoryId()){
				throw new ApiException.ValidationFailure("Invalid book category");
			}
		});
	}

}
