package com.action;

import java.util.*;
import java.util.stream.Collectors;

import javax.annotation.Resource;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

import com.entity.Article;
import com.entity.Cart;
import com.entity.Cate;
import com.entity.City;
import com.entity.Details;
import com.entity.Film;
import com.entity.Orders;
import com.entity.Topic;
import com.entity.Users;
import com.service.ArticleService;
import com.service.CartService;
import com.service.CateService;
import com.service.CinemaService;
import com.service.CityService;
import com.service.DetailsService;
import com.service.FilmService;
import com.service.OrdersService;
import com.service.TopicService;
import com.service.UsersService;
import com.util.VeDate;

//定义为控制器
@Controller
// 设置路径
@RequestMapping("/index")
public class IndexAction extends BaseAction {

	@Autowired
	@Resource
	private UsersService usersService;
	@Autowired
	@Resource
	private ArticleService articleService;
	@Autowired
	@Resource
	private CateService cateService;
	@Autowired
	@Resource
	private CityService cityService;
	@Autowired
	@Resource
	private CinemaService cinemaService;
	@Autowired
	@Resource
	private FilmService filmService;
	@Autowired
	@Resource
	private CartService cartService;
	@Autowired
	@Resource
	private OrdersService ordersService;
	@Autowired
	@Resource
	private DetailsService detailsService;
	@Autowired
	@Resource
	private TopicService topicService;

	// 公共方法 提供公共查询数据
	private void front() {
		this.getRequest().setAttribute("title", "在线电影订票系统");
		List<Cate> cateList = this.cateService.getAllCate();
		this.getRequest().setAttribute("cateList", cateList);
		List<Film> hotList = this.filmService.getFilmByHot();
		this.getRequest().setAttribute("hotList", hotList);
	}

	// 首页显示
	@RequestMapping("index.action")
	public String index() {
		this.front();
		List<Cate> cateList = this.cateService.getCateFront();
		List<Cate> frontList = new ArrayList<Cate>();
		for (Cate cate : cateList) {
			List<Film> flimList = this.filmService.getFilmByCate(cate.getCateid());
			cate.setFlimList(flimList);
			frontList.add(cate);
		}
		List<Film> recommendFileList = new ArrayList<>();
		String userId = (String) this.getSession().getAttribute("userid");
		// 判断用户是否登录，如果没有登录，则推荐热门电影，如果已经登录，则通过算法算来推荐
		if (userId == null) {
			recommendFileList = filmService.getFilmByHot();
		} else {
			// 获取所有用户信息
			List<Users> userList = usersService.getAllUsers();
			// 获取所有电影
			List<Film> filmList = filmService.getAllFilm();
			// 获取所有评分
			List<Topic> topicList = topicService.getAllTopic();
			// 构建一个矩阵用于每一个用户对电影的评分
			double[][] score = new double[userList.size()][filmList.size()];
			List<FilmScore> userAverageScore = new ArrayList<>();
			// 因为一个用户可能对同一个电影有多个评分，而每个评分又不同，所以先要计算每个用户对每个电影的平均分
			Map<String, List<Topic>> userGroup = topicList.stream().collect(Collectors.groupingBy(Topic::getUsersid));
			for (Map.Entry<String, List<Topic>> entry : userGroup.entrySet()) {
				List<Topic> topics = entry.getValue();
				Map<String, List<Topic>> filmGroup = topics.stream().collect(Collectors.groupingBy(Topic::getFilmid));
				for (Map.Entry<String, List<Topic>> entry1 : filmGroup.entrySet()) {
					FilmScore filmScore = new FilmScore();
					filmScore.setUserId(entry.getKey());
					filmScore.setFilmId(entry1.getKey());
					double sum = 0d;
					for (Topic topic : entry1.getValue()) {
						sum += Integer.parseInt(topic.getNum());
					}
					filmScore.setScore(sum/entry1.getValue().size());
					userAverageScore.add(filmScore);
				}
			}
			int index = 0;
			// 循环遍历所有的评分，将每个用户对电影的平均评分填入数组中
			for (int i = 0; i < userList.size(); i++) {
				if (userList.get(i).getUsersid().equals(userId)) {
					// 获取到当前用户所对应的那一列数据，用于后面计算时可以排除该该列数据
					index = i;
				}
				for (int j = 0; j < filmList.size(); j++) {
					// 这个时候不能直接将评价中的评分填入，而是要将上面计算出来的平均评分填入
					for (FilmScore filmScore : userAverageScore) {
						if (userList.get(i).getUsersid().equals(filmScore.getUserId()) && filmList.get(j).getFilmid().equals(filmScore.getFilmId())) {
							score[i][j] += filmScore.getScore();
						}
					}
				}
			}

			Map<Double, String> similarScore = new TreeMap<>(Comparator.reverseOrder());
			double[] arr = score[index];
			// 在这里通过余弦公式计算用户相似度
			// 余弦相似度  参考博客： https://blog.csdn.net/zz_dd_yy/article/details/51926305
			for (int i = 0; i < score.length; i++) {
				// 这个判断是用于过滤自己的，因为自己不能跟自己比，不然相似度肯定是为1
				if (i == index) {
					continue;
				}
				// 分子
				double molecule =0;
				double denominator1 = 0;
				double denominator2 = 0;
				for (int k = 0; k < arr.length; k++) {
					denominator1 += Math.pow(arr[k], 2);
					denominator2 += Math.pow(score[i][k], 2);
					molecule += arr[k] * score[i][k];
				}

				// 分母
				double denominator = Math.sqrt(denominator1) * Math.sqrt(denominator2);
				if (denominator != 0) {
					double resultScore = molecule / denominator;
					similarScore.put(resultScore, userList.get(i).getUsersid());
				}
			}
			System.out.println(similarScore);
			// 上面是求出用户间的相似度，接下来需要根据这些用户的评分结合相似度来预测用户对为评论过的电影的评分
			// 首先先要求出当前用户对以往评分的平均分
			// 协同过滤推荐算法：评分预测标准化   参考博客  https://zhuanlan.zhihu.com/p/58630558
			List<Topic> list = userGroup.get(userId);
			// 如果该用户没有评估过，那就拿整个系统的平均分作为他的平均分
			if (list == null) {
				list = topicList;
			}
			// 如果该用户没有评估过，那就拿整个系统的平均分作为他的平均分
			double sum = 0;
			for (Topic topic : list) {
				sum += Integer.parseInt(topic.getNum());
			}
			double currentUserAverageScore = sum / list.size();
			// 循环所有的电影类型，计算预测评分，如果已经有自己的评分则不用预测
			Map<String, Double> filmScoreMap = new HashMap<>();
			for (Film film : filmList) {
				for (FilmScore filmScore : userAverageScore) {
					if (filmScore.getUserId().equals(userId) && filmScore.getFilmId().equals(film.getFilmid())) {
						filmScoreMap.put(film.getFilmid(), filmScore.getScore());
						break;
					}
				}
				// 如果已经自己有过评分了，则不需要再去根据其他用户的相似度进行评分预测
				if (filmScoreMap.get(film.getFilmid()) == null) {
					double molecule = 0;
					double denominator = 0;
					for (Map.Entry<Double, String> entry : similarScore.entrySet()) {
						// 这个时候就需要按评分标准化方法中的均值中心化公式来计算
						// 计算entry中用户的平均评分
						List<Topic> topics = userGroup.get(entry.getValue());
						List<String> scoreList = topics.stream().map(Topic::getNum).collect(Collectors.toList());
						double tempSum = 0;
						for (String i : scoreList) {
							tempSum += Integer.parseInt(i);
						}
						double similarUserAverageScore = tempSum / scoreList.size();
						double userItemScore = 0;
						// 另外还要求出当前相似用户对这个电影的评分
						for (FilmScore filmScore : userAverageScore) {
							if (filmScore.getUserId().equals(entry.getValue()) && filmScore.getFilmId().equals(film.getFilmid())) {
								userItemScore = filmScore.getScore();
							}
						}
						// 如果这个相似用户对这个商品也没有评过分，则以他的整体平均分为数据
						if (userItemScore == 0) {
							userItemScore = similarUserAverageScore;
						}
						molecule += entry.getKey() * (userItemScore - similarUserAverageScore);
						denominator += entry.getKey();
					}
					double p = 0;
					if (denominator == 0) {
						p = currentUserAverageScore;
					} else {
						p = currentUserAverageScore + molecule / denominator;
					}

					filmScoreMap.put(film.getFilmid(), p);
				}
			}
			System.out.println(filmScoreMap);
			for (Film film : filmList) {
				film.setScore(filmScoreMap.get(film.getFilmid()));
			}
			// 接下来进行排序
			filmList.sort((o1, o2) -> o2.getScore().compareTo(o1.getScore()));
			for (Film film : filmList) {
				System.out.println("评分：" + film.getScore() + " ---> " + film.getFilmname());
			}
			List<Topic> topics = userGroup.get(userId);
			List<String> collect = topics.stream().map(Topic::getFilmid).collect(Collectors.toList());
			// 过滤掉已评分的电影

			filmList = filmList.stream().filter(item ->  !collect.contains(item.getFilmid())).collect(Collectors.toList());
			recommendFileList = filmList.subList(0, 5);
		}

		this.getRequest().setAttribute("recommendFileList", recommendFileList);
		this.getRequest().setAttribute("frontList", frontList);
		return "users/index";
	}

	static class FilmScore {
		private String userId;
		private String filmId;
		private double score;

		public String getUserId() {
			return userId;
		}

		public void setUserId(String userId) {
			this.userId = userId;
		}

		public String getFilmId() {
			return filmId;
		}

		public void setFilmId(String filmId) {
			this.filmId = filmId;
		}

		public double getScore() {
			return score;
		}

		public void setScore(double score) {
			this.score = score;
		}
	}

	// 公告
	@RequestMapping("article.action")
	public String article(String number) {
		this.front();
		List<Article> articleList = new ArrayList<Article>();
		List<Article> tempList = this.articleService.getAllArticle();
		int pageNumber = tempList.size();
		int maxPage = pageNumber;
		if (maxPage % 12 == 0) {
			maxPage = maxPage / 12;
		} else {
			maxPage = maxPage / 12 + 1;
		}
		if (number == null) {
			number = "0";
		}
		int start = Integer.parseInt(number) * 12;
		int over = (Integer.parseInt(number) + 1) * 12;
		int count = pageNumber - over;
		if (count <= 0) {
			over = pageNumber;
		}
		for (int i = start; i < over; i++) {
			Article x = tempList.get(i);
			articleList.add(x);
		}
		String html = "";
		StringBuffer buffer = new StringBuffer();
		buffer.append("&nbsp;&nbsp;共为");
		buffer.append(maxPage);
		buffer.append("页&nbsp; 共有");
		buffer.append(pageNumber);
		buffer.append("条&nbsp; 当前为第");
		buffer.append((Integer.parseInt(number) + 1));
		buffer.append("页 &nbsp;");
		if ((Integer.parseInt(number) + 1) == 1) {
			buffer.append("首页");
		} else {
			buffer.append("<a href=\"index/article.action?number=0\">首页</a>");
		}
		buffer.append("&nbsp;&nbsp;");
		if ((Integer.parseInt(number) + 1) == 1) {
			buffer.append("上一页");
		} else {
			buffer.append("<a href=\"index/article.action?number=" + (Integer.parseInt(number) - 1) + "\">上一页</a>");
		}
		buffer.append("&nbsp;&nbsp;");
		if (maxPage <= (Integer.parseInt(number) + 1)) {
			buffer.append("下一页");
		} else {
			buffer.append("<a href=\"index/article.action?number=" + (Integer.parseInt(number) + 1) + "\">下一页</a>");
		}
		buffer.append("&nbsp;&nbsp;");
		if (maxPage <= (Integer.parseInt(number) + 1)) {
			buffer.append("尾页");
		} else {
			buffer.append("<a href=\"index/article.action?number=" + (maxPage - 1) + "\">尾页</a>");
		}
		html = buffer.toString();
		this.getRequest().setAttribute("html", html);
		this.getRequest().setAttribute("articleList", articleList);
		return "users/article";
	}

	// 阅读公告
	@RequestMapping("read.action")
	public String read(String id) {
		this.front();
		Article article = this.articleService.getArticleById(id);
		article.setHits("" + (Integer.parseInt(article.getHits()) + 1));
		this.articleService.updateArticle(article);
		this.getRequest().setAttribute("article", article);
		return "users/read";
	}

	// 准备登录
	@RequestMapping("preLogin.action")
	public String prelogin() {
		this.front();
		return "users/login";
	}

	// 用户登录
	@RequestMapping("login.action")
	public String login() {
		this.front();
		String username = this.getRequest().getParameter("username");
		String password = this.getRequest().getParameter("password");
		Users u = new Users();
		u.setUsername(username);
		List<Users> usersList = this.usersService.getUsersByCond(u);
		if (usersList.size() == 0) {
			this.getSession().setAttribute("message", "用户名不存在");
			return "redirect:/index/preLogin.action";
		} else {
			Users users = usersList.get(0);
			if (password.equals(users.getPassword())) {
				this.getSession().setAttribute("userid", users.getUsersid());
				this.getSession().setAttribute("username", users.getUsername());
				this.getSession().setAttribute("users", users);
				return "redirect:/index/index.action";
			} else {
				this.getSession().setAttribute("message", "密码错误");
				return "redirect:/index/preLogin.action";
			}
		}
	}

	// 准备注册
	@RequestMapping("preReg.action")
	public String preReg() {
		this.front();
		return "users/register";
	}

	// 用户注册
	@RequestMapping("register.action")
	public String register(Users users) {
		this.front();
		Users u = new Users();
		u.setUsername(users.getUsername());
		List<Users> usersList = this.usersService.getUsersByCond(u);
		if (usersList.size() == 0) {
			users.setRegdate(VeDate.getStringDateShort());
			this.usersService.insertUsers(users);
		} else {
			this.getSession().setAttribute("message", "用户名已存在");
			return "redirect:/index/preReg.action";
		}

		return "redirect:/index/preLogin.action";
	}

	// 退出登录
	@RequestMapping("exit.action")
	public String exit() {
		this.front();
		this.getSession().removeAttribute("userid");
		this.getSession().removeAttribute("username");
		this.getSession().removeAttribute("users");
		return "index";
	}

	// 准备修改密码
	@RequestMapping("prePwd.action")
	public String prePwd() {
		this.front();
		if (this.getSession().getAttribute("userid") == null) {
			return "redirect:/index/preLogin.action";
		}
		return "users/editpwd";
	}

	// 修改密码
	@RequestMapping("editpwd.action")
	public String editpwd() {
		this.front();
		if (this.getSession().getAttribute("userid") == null) {
			return "redirect:/index/preLogin.action";
		}
		String userid = (String) this.getSession().getAttribute("userid");
		String password = this.getRequest().getParameter("password");
		String repassword = this.getRequest().getParameter("repassword");
		Users users = this.usersService.getUsersById(userid);
		if (password.equals(users.getPassword())) {
			users.setPassword(repassword);
			this.usersService.updateUsers(users);
		} else {
			this.getSession().setAttribute("message", "旧密码错误");
			return "redirect:/index/prePwd.action";
		}
		return "redirect:/index/prePwd.action";
	}

	@RequestMapping("usercenter.action")
	public String usercenter() {
		this.front();
		if (this.getSession().getAttribute("userid") == null) {
			return "redirect:/index/preLogin.action";
		}
		return "users/usercenter";
	}

	@RequestMapping("userinfo.action")
	public String userinfo() {
		this.front();
		if (this.getSession().getAttribute("userid") == null) {
			return "redirect:/index/preLogin.action";
		}
		String userid = (String) this.getSession().getAttribute("userid");
		this.getSession().setAttribute("users", this.usersService.getUsersById(userid));
		return "users/userinfo";
	}

	@RequestMapping("personal.action")
	public String personal(Users users) {
		this.front();
		if (this.getSession().getAttribute("userid") == null) {
			return "redirect:/index/preLogin.action";
		}
		this.usersService.updateUsers(users);
		return "redirect:/index/userinfo.action";
	}

	// 添加产品到购物车
	@RequestMapping("addcart.action")
	public String addcart() {
		this.front();
		if (this.getSession().getAttribute("userid") == null) {
			return "redirect:/index/preLogin.action";
		}
		String userid = (String) this.getSession().getAttribute("userid");
		Cart cart = new Cart();
		cart.setFilmid(getRequest().getParameter("goodsid"));
		cart.setNum(getRequest().getParameter("num"));
		cart.setPrice(getRequest().getParameter("price"));
		cart.setUsersid(userid);
		this.cartService.insertCart(cart);
		return "redirect:/index/cart.action";
	}

	// 查看购物车
	@RequestMapping("cart.action")
	public String cart() {
		this.front();
		if (this.getSession().getAttribute("userid") == null) {
			return "redirect:/index/preLogin.action";
		}
		String userid = (String) this.getSession().getAttribute("userid");
		Cart cart = new Cart();
		cart.setUsersid(userid);
		List<Cart> cartList = this.cartService.getCartByCond(cart);
		this.getRequest().setAttribute("cartList", cartList);
		return "users/cart";
	}

	// 删除购物车中的产品
	@RequestMapping("deletecart.action")
	public String deletecart(String id) {
		this.front();
		if (this.getSession().getAttribute("userid") == null) {
			return "redirect:/index/preLogin.action";
		}
		this.cartService.deleteCart(id);
		return "redirect:/index/cart.action";
	}

	// 准备结算
	@RequestMapping("preCheckout.action")
	public String preCheckout() {
		this.front();
		if (this.getSession().getAttribute("userid") == null) {
			return "redirect:/index/preLogin.action";
		}
		String userid = (String) this.getSession().getAttribute("userid");
		Cart cart = new Cart();
		cart.setUsersid(userid);
		List<Cart> cartList = this.cartService.getCartByCond(cart);
		if (cartList.size() == 0) {
			this.getSession().setAttribute("message", "请选购商品");
			return "redirect:/index/cart.action";
		}
		List<City> cityList = this.cityService.getAllCity();
		this.getRequest().setAttribute("cityList", cityList);
		return "users/checkout";
	}

	// 结算
	@RequestMapping("checkout.action")
	public String checkout() {
		this.front();
		if (this.getSession().getAttribute("userid") == null) {
			return "redirect:/index/preLogin.action";
		}
		String userid = (String) this.getSession().getAttribute("userid");
		Cart cart1 = new Cart();
		cart1.setUsersid(userid);
		List<Cart> cartList = this.cartService.getCartByCond(cart1);
		if (cartList.size() == 0) {
			this.getRequest().setAttribute("message", "请选购商品");
			return "redirect:/index/cart.action";
		} else {
			// 获取一个1200-9999的随机数 防止同时提交
			String ordercode = "PD" + VeDate.getStringDatex();
			double total = 0;
			for (Cart cart : cartList) {
				Details details = new Details();
				details.setDetailsid(VeDate.getStringDatex() + (Math.random() * 9 + 1) * 1200);
				details.setFilmid(cart.getFilmid());
				details.setNum(cart.getNum());
				details.setOrdercode(ordercode);
				details.setPrice(cart.getPrice());
				details.setCinemaid(this.getRequest().getParameter("cinemaid"));
				details.setCityid(this.getRequest().getParameter("cityid"));
				details.setViewdate(this.getRequest().getParameter("viewdate"));
				this.detailsService.insertDetails(details);
				Film goods = this.filmService.getFilmById(cart.getFilmid());
				goods.setSellnum("" + (Integer.parseInt(goods.getSellnum()) + Integer.parseInt(cart.getNum())));
				this.filmService.updateFilm(goods);
				total += Double.parseDouble(cart.getPrice()) * Double.parseDouble(cart.getNum());
				this.cartService.deleteCart(cart.getCartid());
			}
			Orders orders = new Orders();
			orders.setAddtime(VeDate.getStringDateShort());
			orders.setOrdercode(ordercode);
			orders.setStatus("未付款");
			orders.setTotal("" + total);
			orders.setUsersid(userid);
			this.ordersService.insertOrders(orders);
		}
		return "redirect:/index/showOrders.action";
	}

	// 查看订购
	@RequestMapping("showOrders.action")
	public String showOrders(String number) {
		this.front();
		if (this.getSession().getAttribute("userid") == null) {
			return "redirect:/index/preLogin.action";
		}
		String userid = (String) this.getSession().getAttribute("userid");
		Orders orders = new Orders();
		orders.setUsersid(userid);
		List<Orders> ordersList = new ArrayList<Orders>();
		List<Orders> tempList = this.ordersService.getOrdersByCond(orders);
		int pageNumber = tempList.size();
		int maxPage = pageNumber;
		if (maxPage % 12 == 0) {
			maxPage = maxPage / 12;
		} else {
			maxPage = maxPage / 12 + 1;
		}
		if (number == null) {
			number = "0";
		}
		int start = Integer.parseInt(number) * 12;
		int over = (Integer.parseInt(number) + 1) * 12;
		int count = pageNumber - over;
		if (count <= 0) {
			over = pageNumber;
		}
		for (int i = start; i < over; i++) {
			Orders o = tempList.get(i);
			ordersList.add(o);
		}
		String html = "";
		StringBuffer buffer = new StringBuffer();
		buffer.append("&nbsp;&nbsp;共为");
		buffer.append(maxPage);
		buffer.append("页&nbsp; 共有");
		buffer.append(pageNumber);
		buffer.append("条&nbsp; 当前为第");
		buffer.append((Integer.parseInt(number) + 1));
		buffer.append("页 &nbsp;");
		if ((Integer.parseInt(number) + 1) == 1) {
			buffer.append("首页");
		} else {
			buffer.append("<a href=\"index/showOrders.action?number=0\">首页</a>");
		}
		buffer.append("&nbsp;&nbsp;");
		if ((Integer.parseInt(number) + 1) == 1) {
			buffer.append("上一页");
		} else {
			buffer.append("<a href=\"index/showOrders.action?number=" + (Integer.parseInt(number) - 1) + "\">上一页</a>");
		}
		buffer.append("&nbsp;&nbsp;");
		if (maxPage <= (Integer.parseInt(number) + 1)) {
			buffer.append("下一页");
		} else {
			buffer.append("<a href=\"index/showOrders.action?number=" + (Integer.parseInt(number) + 1) + "\">下一页</a>");
		}
		buffer.append("&nbsp;&nbsp;");
		if (maxPage <= (Integer.parseInt(number) + 1)) {
			buffer.append("尾页");
		} else {
			buffer.append("<a href=\"index/showOrders.action?number=" + (maxPage - 1) + "\">尾页</a>");
		}
		html = buffer.toString();
		this.getRequest().setAttribute("html", html);
		this.getRequest().setAttribute("ordersList", ordersList);
		return "users/orderlist";
	}

	// 准备付款
	@RequestMapping("prePay.action")
	public String prePay(String id) {
		this.front();
		if (this.getSession().getAttribute("userid") == null) {
			return "redirect:/index/preLogin.action";
		}
		this.getRequest().setAttribute("id", id);
		return "users/pay";
	}

	// 付款
	@RequestMapping("pay.action")
	public String pay(String id) {
		this.front();
		if (this.getSession().getAttribute("userid") == null) {
			return "redirect:/index/preLogin.action";
		}
		Orders orders = this.ordersService.getOrdersById(this.getRequest().getParameter("id"));
		orders.setStatus("已付款");
		this.ordersService.updateOrders(orders);
		return "redirect:/index/showOrders.action";
	}

	// 确认收货
	@RequestMapping("over.action")
	public String over(String id) {
		this.front();
		if (this.getSession().getAttribute("userid") == null) {
			return "redirect:/index/preLogin.action";
		}
		Orders orders = this.ordersService.getOrdersById(this.getRequest().getParameter("id"));
		orders.setStatus("已收货");
		this.ordersService.updateOrders(orders);
		return "redirect:/index/showOrders.action";
	}

	// 取消订单
	@RequestMapping("cancel.action")
	public String cancel(String id) {
		this.front();
		if (this.getSession().getAttribute("userid") == null) {
			return "redirect:/index/preLogin.action";
		}
		Orders orders = this.ordersService.getOrdersById(this.getRequest().getParameter("id"));
		orders.setStatus("已取消");
		this.ordersService.updateOrders(orders);
		return "redirect:/index/showOrders.action";
	}

	// 订单明细
	@RequestMapping("orderdetail.action")
	public String orderdetail(String id) {
		this.front();
		if (this.getSession().getAttribute("userid") == null) {
			return "redirect:/index/preLogin.action";
		}
		Details details = new Details();
		details.setOrdercode(id);
		List<Details> detailsList = this.detailsService.getDetailsByCond(details);
		this.getRequest().setAttribute("detailsList", detailsList);
		return "users/orderdetail";
	}

	// 按分类查询
	@RequestMapping("cate.action")
	public String cate(String id, String number) {
		this.front();
		Film goods = new Film();
		goods.setCateid(id);
		List<Film> flimList = new ArrayList<Film>();
		List<Film> tempList = this.filmService.getFilmByCond(goods);
		int pageNumber = tempList.size();
		int maxPage = pageNumber;
		if (maxPage % 12 == 0) {
			maxPage = maxPage / 12;
		} else {
			maxPage = maxPage / 12 + 1;
		}
		if (number == null) {
			number = "0";
		}
		int start = Integer.parseInt(number) * 12;
		int over = (Integer.parseInt(number) + 1) * 12;
		int count = pageNumber - over;
		if (count <= 0) {
			over = pageNumber;
		}
		for (int i = start; i < over; i++) {
			Film x = tempList.get(i);
			flimList.add(x);
		}
		String html = "";
		StringBuffer buffer = new StringBuffer();
		buffer.append("&nbsp;&nbsp;共为");
		buffer.append(maxPage);
		buffer.append("页&nbsp; 共有");
		buffer.append(pageNumber);
		buffer.append("条&nbsp; 当前为第");
		buffer.append((Integer.parseInt(number) + 1));
		buffer.append("页 &nbsp;");
		if ((Integer.parseInt(number) + 1) == 1) {
			buffer.append("首页");
		} else {
			buffer.append("<a href=\"index/cate.action?number=0&id=\" + id+ \"\">首页</a>");
		}
		buffer.append("&nbsp;&nbsp;");
		if ((Integer.parseInt(number) + 1) == 1) {
			buffer.append("上一页");
		} else {
			buffer.append("<a href=\"index/cate.action?number=" + (Integer.parseInt(number) - 1) + "&id=\" + id+ \"\">上一页</a>");
		}
		buffer.append("&nbsp;&nbsp;");
		if (maxPage <= (Integer.parseInt(number) + 1)) {
			buffer.append("下一页");
		} else {
			buffer.append("<a href=\"index/cate.action?number=" + (Integer.parseInt(number) + 1) + "&id=\" + id+ \"\">下一页</a>");
		}
		buffer.append("&nbsp;&nbsp;");
		if (maxPage <= (Integer.parseInt(number) + 1)) {
			buffer.append("尾页");
		} else {
			buffer.append("<a href=\"index/cate.action?number=" + (maxPage - 1) + "&id=\" + id+ \"\">尾页</a>");
		}
		html = buffer.toString();
		this.getRequest().setAttribute("html", html);
		this.getRequest().setAttribute("flimList", flimList);
		return "users/list";
	}

	// 推荐产品
	@RequestMapping("recommend.action")
	public String recommend(String number) {
		this.front();
		Film goods = new Film();
		goods.setRecommend("是");
		List<Film> flimList = new ArrayList<Film>();
		List<Film> tempList = this.filmService.getFilmByCond(goods);
		int pageNumber = tempList.size();
		int maxPage = pageNumber;
		if (maxPage % 12 == 0) {
			maxPage = maxPage / 12;
		} else {
			maxPage = maxPage / 12 + 1;
		}
		if (number == null) {
			number = "0";
		}
		int start = Integer.parseInt(number) * 12;
		int over = (Integer.parseInt(number) + 1) * 12;
		int count = pageNumber - over;
		if (count <= 0) {
			over = pageNumber;
		}
		for (int i = start; i < over; i++) {
			Film x = tempList.get(i);
			flimList.add(x);
		}
		String html = "";
		StringBuffer buffer = new StringBuffer();
		buffer.append("&nbsp;&nbsp;共为");
		buffer.append(maxPage);
		buffer.append("页&nbsp; 共有");
		buffer.append(pageNumber);
		buffer.append("条&nbsp; 当前为第");
		buffer.append((Integer.parseInt(number) + 1));
		buffer.append("页 &nbsp;");
		if ((Integer.parseInt(number) + 1) == 1) {
			buffer.append("首页");
		} else {
			buffer.append("<a href=\"index/recommend.action?number=0\">首页</a>");
		}
		buffer.append("&nbsp;&nbsp;");
		if ((Integer.parseInt(number) + 1) == 1) {
			buffer.append("上一页");
		} else {
			buffer.append("<a href=\"index/recommend.action?number=" + (Integer.parseInt(number) - 1) + "\">上一页</a>");
		}
		buffer.append("&nbsp;&nbsp;");
		if (maxPage <= (Integer.parseInt(number) + 1)) {
			buffer.append("下一页");
		} else {
			buffer.append("<a href=\"index/recommend.action?number=" + (Integer.parseInt(number) + 1) + "\">下一页</a>");
		}
		buffer.append("&nbsp;&nbsp;");
		if (maxPage <= (Integer.parseInt(number) + 1)) {
			buffer.append("尾页");
		} else {
			buffer.append("<a href=\"index/recommend.action?number=" + (maxPage - 1) + "\">尾页</a>");
		}
		html = buffer.toString();
		this.getRequest().setAttribute("html", html);
		this.getRequest().setAttribute("flimList", flimList);
		return "users/list";
	}

	// 全部产品
	@RequestMapping("all.action")
	public String all(String number) {
		this.front();
		List<Film> flimList = new ArrayList<Film>();
		List<Film> tempList = this.filmService.getAllFilm();
		int pageNumber = tempList.size();
		int maxPage = pageNumber;
		if (maxPage % 12 == 0) {
			maxPage = maxPage / 12;
		} else {
			maxPage = maxPage / 12 + 1;
		}
		if (number == null) {
			number = "0";
		}
		int start = Integer.parseInt(number) * 12;
		int over = (Integer.parseInt(number) + 1) * 12;
		int count = pageNumber - over;
		if (count <= 0) {
			over = pageNumber;
		}
		for (int i = start; i < over; i++) {
			Film x = tempList.get(i);
			flimList.add(x);
		}
		String html = "";
		StringBuffer buffer = new StringBuffer();
		buffer.append("&nbsp;&nbsp;共为");
		buffer.append(maxPage);
		buffer.append("页&nbsp; 共有");
		buffer.append(pageNumber);
		buffer.append("条&nbsp; 当前为第");
		buffer.append((Integer.parseInt(number) + 1));
		buffer.append("页 &nbsp;");
		if ((Integer.parseInt(number) + 1) == 1) {
			buffer.append("首页");
		} else {
			buffer.append("<a href=\"index/recommend.action?number=0\">首页</a>");
		}
		buffer.append("&nbsp;&nbsp;");
		if ((Integer.parseInt(number) + 1) == 1) {
			buffer.append("上一页");
		} else {
			buffer.append("<a href=\"index/recommend.action?number=" + (Integer.parseInt(number) - 1) + "\">上一页</a>");
		}
		buffer.append("&nbsp;&nbsp;");
		if (maxPage <= (Integer.parseInt(number) + 1)) {
			buffer.append("下一页");
		} else {
			buffer.append("<a href=\"index/recommend.action?number=" + (Integer.parseInt(number) + 1) + "\">下一页</a>");
		}
		buffer.append("&nbsp;&nbsp;");
		if (maxPage <= (Integer.parseInt(number) + 1)) {
			buffer.append("尾页");
		} else {
			buffer.append("<a href=\"index/recommend.action?number=" + (maxPage - 1) + "\">尾页</a>");
		}
		html = buffer.toString();
		this.getRequest().setAttribute("html", html);
		this.getRequest().setAttribute("flimList", flimList);
		return "users/list";
	}

	// 查询商品
	@RequestMapping("query.action")
	public String query(String name) {
		this.front();
		Film goods = new Film();
		goods.setFilmname(name);
		List<Film> flimList = this.filmService.getFilmByLike(goods);
		this.getRequest().setAttribute("flimList", flimList);
		return "users/list";
	}

	// 商品详情
	@RequestMapping("detail.action")
	public String detail(String id) {
		this.front();
		Film goods = this.filmService.getFilmById(id);
		goods.setHits("" + (Integer.parseInt(goods.getHits()) + 1));
		this.filmService.updateFilm(goods);
		this.getRequest().setAttribute("goods", goods);
		Topic topic = new Topic();
		topic.setFilmid(id);
		List<Topic> topicList = this.topicService.getTopicByCond(topic);
		this.getRequest().setAttribute("topicList", topicList);
		this.getRequest().setAttribute("tnum", topicList.size());
		return "users/detail";
	}

	@RequestMapping("addTopic.action")
	public String addTopic(Topic topic) {
		this.front();
		if (this.getSession().getAttribute("userid") == null) {
			return "redirect:/index/preLogin.action";
		}
		String userid = (String) this.getSession().getAttribute("userid");
		topic.setAddtime(VeDate.getStringDateShort());
		topic.setContents(this.getRequest().getParameter("contents"));
		topic.setFilmid(this.getRequest().getParameter("goodsid"));
		topic.setNum(this.getRequest().getParameter("num"));
		topic.setUsersid(userid);
		this.topicService.insertTopic(topic);
		return "redirect:/index/detail.action?id=" + topic.getFilmid();
	}

}
