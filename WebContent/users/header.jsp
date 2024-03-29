<%@ page language="java" import="java.util.*" pageEncoding="utf-8"%>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core"%>
<%
	String path = request.getContextPath();
	String basePath = request.getScheme() + "://" + request.getServerName() + ":" + request.getServerPort() + path + "/";
%>
<link href="themes/xecmoban_haier2015/style.css" rel="stylesheet" type="text/css" />



<div class="ng-nav-bar" style="height:50x; width:1200px">
	<div class="block">
	
		<div class="ng-nav-index" >
		<img src="themes/xecmoban_haier2015/images/logo.jpeg" style="height:50px; width:50px;vertical-align:middle; float:left;" /> 
			<ul class="ng-nav">
				<li><a href="index.jsp" class="cur">首页</a></li>
				<li><a href="index/article.action" class="cur">网站公告</a></li>
				<li><a href="index/recommend.action" class="cur">推荐电影</a></li>
				<li><a href="index/all.action" class="cur">全部电影</a></li>
			</ul>
			<div class="ng-search">
				<div class="g-search">
					<form id="searchForm" name="searchForm" method="post" action="index/query.action">
						<div class="search-keyword-box">
							<input name="name" type="text" id="name" value="" class="search-keyword" />
						</div>
						<input type="submit" value="搜索" class="btn-search" style="cursor: pointer;" />
						<div class="clear"></div>
					</form>
				</div>
			</div>
			<div  class="userl">
				<c:if test="${sessionScope.userid == null }">
				<span> <a style="color: #FF6766" href="index/preLogin.action">[用户登录]</a> <a style="color: #FF6766;"
						href="index/preReg.action">[用户注册]</a>
				</span>

				</c:if>
				<c:if test="${sessionScope.userid != null }">
					<span><a style="color: #FF6766;" href="index/cart.action">[购物车]</a><a style="color: #FF6766;"
						href="index/usercenter.action">[用户中心]</a> <a style="color: #FF6766;" href="index/exit.action">[退出系统] </a> </span>
				</c:if>
			</div>
	</div>
   </div>
<div class="blank"></div>
<%
	String message = (String) session.getAttribute("message");
	if (message == null) {
		message = "";
	}
	if (!message.trim().equals("")) {
		out.println("<script language='javascript'>");
		out.println("alert('" + message + "');");
		out.println("</script>");
	}
	request.removeAttribute("message");
%>