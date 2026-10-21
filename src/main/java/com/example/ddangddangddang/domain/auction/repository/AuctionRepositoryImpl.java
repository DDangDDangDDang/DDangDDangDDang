package com.example.ddangddangddang.domain.auction.repository;

import com.example.ddangddangddang.domain.auction.dto.request.AuctionSearchCondition;
import com.example.ddangddangddang.domain.auction.dto.response.AuctionListResponse;
import com.example.ddangddangddang.domain.auction.entity.AuctionSort;
import com.example.ddangddangddang.domain.auction.entity.AuctionStatus;
import com.example.ddangddangddang.domain.auction.entity.QAuction;
import com.example.ddangddangddang.domain.category.entity.QCategory;
import com.example.ddangddangddang.domain.product.entity.QProduct;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.support.PageableExecutionUtils;

import javax.swing.undo.AbstractUndoableEdit;
import java.util.List;


@RequiredArgsConstructor
public class AuctionRepositoryImpl implements AuctionRepositoryCustom{
    private final JPAQueryFactory queryFactory;

    /**경매 목록 조회 및 검색
    * 검색 조건은 모두 선택,값이 없을 시 기본값을 적용 **/
    @Override
    public Page<AuctionListResponse> search(AuctionSearchCondition condition, Pageable pageable) {
        QAuction auction = QAuction.auction;
        QProduct product = QProduct.product;
        QCategory category = QCategory.category;

        //목록 조회 : 입력한 조건에 맞게 정렬후 한페이지 만큼 가져오기
        List<AuctionListResponse> content =queryFactory
                .select(Projections.constructor(AuctionListResponse.class,auction.id,product.id,product.name,category.name,
                        auction.currentPrice,auction.bidCount,auction.status,auction.startAt,auction.endAt,
                        auction.liveStream.isNotNull())) // 라이중의 경매 생성인 경우에는 true(일반 경매 상품은 false)
                .from(auction)
                .join(auction.product,product)
                .join(product.category,category)
                //검색 조건
                .where(
                        productNameContains(product, condition.productName()),
                        categoryEq(category,condition.categoryId()),
                        priceGoe(auction, condition.minPrice()),
                        priceLoe(auction, condition.maxPrice()),
                        statusEq(auction,condition.status()))
                .orderBy(orderBy(auction,condition.sortType()))
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();
        //전체 개수 : 목록과 같은 조건은 해다 데이터 개수만 셈(전체 개수 및 페이지 수 계산용)
        JPAQuery<Long> countQuery = queryFactory.select(auction.count())
                .from(auction)
                .join(auction.product, product)
                .join(product.category, category)
                //개수
                .where(
                        productNameContains(product, condition.productName()),
                        categoryEq(category, condition.categoryId()),
                        priceGoe(auction, condition.minPrice()),
                        priceLoe(auction, condition.maxPrice()),
                        statusEq(auction, condition.status())
                );

        return PageableExecutionUtils.getPage(content,pageable, countQuery::fetchOne);

    }

    /**
     * 인기 경매.
     * 진행 중인 경매 중 입찰 횟수(입찰자 수 아님) 많은 순으로 상위 limi개 조회
     * 순위라서 페이지 없이 상위 n개만 가져온다 */
    @Override
    public List<AuctionListResponse> findPopular(int limit) {
        QAuction auction = QAuction.auction;
        QProduct product = QProduct.product;
        QCategory category = QCategory.category;

        return queryFactory
                .select(Projections.constructor(AuctionListResponse.class,auction.id,product.id,product.name,category.name,auction.currentPrice,auction.bidCount,auction.status,auction.startAt,auction.endAt,auction.liveStream.isNotNull())).
                from(auction)
                .join(auction.product, product)
                .join(product.category,category)
                .where(auction.status.eq(AuctionStatus.ONGOING))
                .orderBy(auction.bidCount.desc(),auction.id.desc()) //입찰 횟수 많은 수 -> 같은면 최근 생성순
                .limit(limit)
                .fetch();
    }

    private BooleanExpression productNameContains(QProduct product,String productName){
        if (productName == null || productName.isBlank()){
            return null;
        }
        return product.name.containsIgnoreCase(productName);
    }

    //카테고리
    private BooleanExpression categoryEq(QCategory category,Long categoryId){
        if (categoryId ==null){
            return null;
        }
        return category.id.eq(categoryId).or(category.parent.id.eq(categoryId));
    }

    //햔제가>=최소가
    private BooleanExpression priceGoe(QAuction auction,Long minPrice){
        return minPrice == null ? null : auction.currentPrice.goe(minPrice);
    }

    //햔제기<=최대가
    private BooleanExpression priceLoe(QAuction auction,Long maxPrice){
        return maxPrice == null ? null : auction.currentPrice.loe(maxPrice);
    }

    //경매 상태: 보낼시 그 상태만, 안보낼시 진행,대기중인 경매들만(종료,실패,취소 상태는 제외)
    private BooleanExpression statusEq(QAuction auction, AuctionStatus status){
        if (status == null){
            return auction.status.in(AuctionStatus.READY,AuctionStatus.ONGOING);
        }
        return auction.status.eq(status);
    }

    //정렬 : 안보낼시 기본 최신순,값이 같을 경우 id 순으로,인기순은 입찰 횟수 많은순
    private OrderSpecifier<?>[] orderBy(QAuction auction, AuctionSort sort){
        if (sort ==null){
            sort = AuctionSort.LATEST;
        }
        return switch (sort){
            case LATEST -> new OrderSpecifier<?>[]{auction.createdAt.desc(),auction.id.desc()};
            case PRICE_ASC -> new OrderSpecifier<?>[]{auction.currentPrice.asc(),auction.id.desc()};
            case PRICE_DESC -> new OrderSpecifier<?>[]{auction.currentPrice.desc(),auction.id.desc()};
            case ENDING_SOON -> new OrderSpecifier<?>[]{auction.endAt.asc(),auction.id.desc()};
            case POPULAR -> new OrderSpecifier<?>[]{auction.bidCount.desc(), auction.id.desc()};
        };
    }
}
